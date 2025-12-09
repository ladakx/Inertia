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
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import com.ladakx.inertia.tools.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Collectors;

public final class InertiaPlugin extends JavaPlugin {

    private static InertiaPlugin instance;
    private JoltNatives joltNatives;
    private PaperCommandManager paperCommandManager;
    private PlayerTools playerTools;
    private JoltTools joltTools;
    private RenderFactory renderFactory;

    @Override
    public void onEnable() {
        instance = this;
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        ConfigManager.init(this);
        ItemManager.init();

        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        setupNMSTools();
        JoltManager.init(this);
        SpaceManager.init(this);
        ToolManager.init(this);

        InertiaAPI.setImplementation(new InertiaAPIImpl(this));
        InertiaLogger.info("Inertia API registered.");

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

    public void reload() {
        ConfigManager.getInstance().reload();
        InertiaLogger.info("Inertia configuration reloaded.");
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
        this.paperCommandManager.registerCommand(new Commands(this));

        // Completions
        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("bodies", c -> ConfigManager.getInstance().getPhysicsBodyRegistry().all().stream()
                .map(PhysicsBodyRegistry.BodyModel::bodyDefinition)
                .map(BodyDefinition::id)
                .collect(Collectors.toList()));

        DebugShapeManager debugShapeManager = new DebugShapeManager();
        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("shapes", c -> debugShapeManager.getAvailableShapes());

        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("items", c -> ItemManager.getInstance().getItemIds());
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
    }

    public static InertiaPlugin getInstance() { return instance; }
    public PlayerTools getPlayerTools() { return instance.playerTools; }
    public JoltTools getJoltTools() { return joltTools; }
    public RenderFactory getRenderFactory() { return renderFactory; }
}