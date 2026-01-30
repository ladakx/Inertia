package com.ladakx.inertia;

import co.aikar.commands.PaperCommandManager;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.impl.InertiaAPIImpl;
import com.ladakx.inertia.commands.Commands;
import com.ladakx.inertia.config.ConfigManager;
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

    // Core Dependencies
    private ConfigManager configManager;
    private JoltManager joltManager;
    private SpaceManager spaceManager;
    private ToolManager toolManager;
    private ItemManager itemManager;

    // NMS / Native
    private JoltNatives joltNatives;
    private PlayerTools playerTools;
    private JoltTools joltTools;
    private RenderFactory renderFactory;

    private PaperCommandManager paperCommandManager;

    @Override
    public void onEnable() {
        instance = this;
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // 1. Config (First, because others depend on it)
        this.configManager = new ConfigManager(this);

        // 2. Items (DI initialization)
        this.itemManager = new ItemManager(configManager);
        this.itemManager.reload();

        // 3. Natives
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 4. NMS
        setupNMSTools();

        // 5. Jolt & Space Managers
        this.joltManager = new JoltManager(this, configManager);
        this.spaceManager = new SpaceManager(this, configManager, joltManager);

        // 6. Tools (Now initialized with dependencies)
        this.toolManager = new ToolManager(this, configManager, spaceManager);

        // 7. API
        InertiaAPI.setImplementation(new InertiaAPIImpl(this, spaceManager, configManager));
        InertiaLogger.info("Inertia API registered.");

        // 8. Commands & Listeners
        registerCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    private void registerCommands() {
        this.paperCommandManager = new PaperCommandManager(this);
        // Completions logic requires configManager instance now
        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("bodies", c ->
                configManager.getPhysicsBodyRegistry().all().stream()
                        .map(PhysicsBodyRegistry.BodyModel::bodyDefinition)
                        .map(BodyDefinition::id)
                        .collect(Collectors.toList()));

        DebugShapeManager debugShapeManager = new DebugShapeManager();
        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("shapes", c -> debugShapeManager.getAvailableShapes());
        this.paperCommandManager.getCommandCompletions().registerAsyncCompletion("items", c -> itemManager.getItemIds());

        // Register Commands with dependencies
        this.paperCommandManager.registerCommand(new Commands(this, configManager, spaceManager, toolManager));
    }

    private void registerListeners() {
        // Register listeners with dependencies
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(spaceManager), this);
    }

    @Override
    public void onDisable() {
        if (spaceManager != null) spaceManager.shutdown();
        if (joltManager != null) joltManager.shutdown();
        InertiaLogger.info("Inertia has been disabled.");
    }

    public void reload() {
        if (configManager != null) configManager.reloadAsync();
        InertiaLogger.info("Inertia configuration reloaded.");
    }

    private boolean setupNativeLibraries() {
        try {
            this.joltNatives = new JoltNatives();
            String precisionStr = this.getConfig().getString("physics.precision", "SP");
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
        this.renderFactory = RenderFactoryInit.get(this.itemManager);
    }

    public static InertiaPlugin getInstance() { return instance; }
    public PlayerTools getPlayerTools() { return playerTools; }
    public JoltTools getJoltTools() { return joltTools; }
    public RenderFactory getRenderFactory() { return renderFactory; }

    // Геттеры для DI
    public ConfigManager getConfigManager() { return configManager; }
    public SpaceManager getSpaceManager() { return spaceManager; }
    public ToolManager getToolManager() { return toolManager; }
    public JoltManager getJoltManager() { return joltManager; }
}