package com.ladakx.inertia;

import co.aikar.commands.PaperCommandManager;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.impl.InertiaAPIImpl;
import com.ladakx.inertia.commands.Commands;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.items.ItemManager;
import com.ladakx.inertia.jolt.JoltManager;
import com.ladakx.inertia.jolt.listeners.WorldLoadListener;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.nativelib.JoltNatives;
import com.ladakx.inertia.nativelib.Precision;
import com.ladakx.inertia.nms.jolt.JoltTools;
import com.ladakx.inertia.nms.jolt.JoltToolsInit;
import com.ladakx.inertia.nms.player.PlayerTools;
import com.ladakx.inertia.nms.player.PlayerToolsInit;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.nms.render.RenderFactoryInit;
import com.ladakx.inertia.physics.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.tools.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Collectors;

/**
 * Main class of the Inertia plugin.
 * Refactored to follow Clean Code principles & Cloud Command Framework.
 */
public final class InertiaPlugin extends JavaPlugin {

    // Singleton instance
    private static InertiaPlugin instance;

    // Jolt
    private JoltNatives joltNatives;

    // Command Manager
    private PaperCommandManager paperCommandManager;

    // NMS & Tools
    private PlayerTools playerTools;
    private JoltTools joltTools;
    private RenderFactory renderFactory;

    private boolean worldEditEnabled;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize Logger
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // Load Integrations (Adventure, WorldEdit)
        setupIntegrations();

        // Load Configurations
        ConfigManager.init(this);
        ItemManager.init();

        // Initialize Native Libraries (Jolt)
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize NMS Tools
        setupNMSTools();

        // Initialize Jolt Manager & Space Manager
        JoltManager.init(this);
        SpaceManager.init(this);

        // Initialize Tool Manager
        ToolManager.init(this);

        // API Registration
        InertiaAPI.setImplementation(new InertiaAPIImpl(this));
        InertiaLogger.info("Inertia API registered.");

        // Register Commands & Listeners
        setupCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        SpaceManager.getInstance().shutdown();
        JoltManager.getInstance().shutdown();
        InertiaLogger.info("Inertia has been disabled.");
    }

    /**
     * Reloads the plugin configurations and spaces.
     */
    public void reload() {
        ConfigManager.getInstance().reload();
        InertiaLogger.info("Inertia configuration reloaded.");
    }

    // ==================================================================
    // Initialization Helpers (Private)
    // ==================================================================

    private void setupIntegrations() {
        var pm = Bukkit.getPluginManager();
        this.worldEditEnabled = pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit");
    }

    private boolean setupNativeLibraries() {
        try {
            this.joltNatives = new JoltNatives();

            String precisionStr = this.getConfig().getString("jolt.precision", "SP");
            Precision precision = "DP".equalsIgnoreCase(precisionStr) ? Precision.DP : Precision.SP;

            InertiaLogger.info("Jolt Precision set to: " + precision);
            this.joltNatives.init(this, precision);
            return true;
        } catch (JoltNatives.JoltNativeException e) {
            InertiaLogger.error("Critical error loading Jolt Natives", e);
            return false;
        }
    }

    private void setupNMSTools() {
        this.joltTools = JoltToolsInit.get();
        this.playerTools = PlayerToolsInit.get();
        this.renderFactory = RenderFactoryInit.get();
    }

    private void setupCommands() {
        this.paperCommandManager = new PaperCommandManager(this);
        this.paperCommandManager.enableUnstableAPI("HelpEntry");
        this.paperCommandManager.enableUnstableAPI("brigadier");
        this.paperCommandManager.enableUnstableAPI("CommandHelp");
        this.paperCommandManager.registerCommand(new Commands());

        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("bodies", c -> {
            return ConfigManager.getInstance().getPhysicsBodyRegistry().all().stream()
                    .map(PhysicsBodyRegistry.BodyModel::bodyDefinition)
                    .map(com.ladakx.inertia.physics.config.BodyDefinition::id)
                    .collect(Collectors.toList());
        });

        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("items", c -> {
            return ItemManager.getInstance().getItemIds();
        });
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
    }

    // ==================================================================
    // Static Accessors (Maintained for backward compatibility)
    // Suggestion: Try to pass 'InertiaPlugin' instance to other classes
    // instead of using these static methods in the future.
    // ==================================================================

    public static InertiaPlugin getInstance() {
        return instance;
    }

    public PlayerTools getPlayerTools() {
        return instance.playerTools;
    }

    public JoltTools getJoltTools() {
        return joltTools;
    }

    public RenderFactory getRenderFactory() {
        return renderFactory;
    }

    public boolean isWorldEditEnabled() {
        return worldEditEnabled;
    }
}