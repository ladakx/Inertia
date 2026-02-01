package com.ladakx.inertia.core;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.common.mesh.BlockBenchMeshProvider;
import com.ladakx.inertia.core.impl.InertiaAPIImpl;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.features.commands.InertiaCommandManager;
import com.ladakx.inertia.features.items.ItemRegistry;
import com.ladakx.inertia.physics.engine.PhysicsEngine;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.listeners.WorldLoadListener;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.infrastructure.nativelib.LibraryLoader;
import com.ladakx.inertia.infrastructure.nativelib.Precision;
import com.ladakx.inertia.infrastructure.nms.jolt.JoltTools;
import com.ladakx.inertia.infrastructure.nms.jolt.JoltToolsInit;
import com.ladakx.inertia.infrastructure.nms.player.PlayerTools;
import com.ladakx.inertia.infrastructure.nms.player.PlayerToolsInit;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.infrastructure.nms.render.RenderFactoryInit;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.features.tools.ToolRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.stream.Collectors;

public final class InertiaPlugin extends JavaPlugin {

    private static InertiaPlugin instance;

    // Core Dependencies
    private ConfigurationService configurationService;
    private PhysicsEngine physicsEngine;
    private PhysicsWorldRegistry physicsWorldRegistry;
    private ToolRegistry toolRegistry;
    private ItemRegistry itemRegistry;

    // NMS / Native
    private LibraryLoader libraryLoader;
    private PlayerTools playerTools;
    private JoltTools joltTools;
    private RenderFactory renderFactory;
    private JShapeFactory shapeFactory;
    private BodyFactory bodyFactory;
    private BlockBenchMeshProvider meshProvider;

    // Commands
    private InertiaCommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // 0. Prepare low-level services
        this.meshProvider = new BlockBenchMeshProvider(this);
        this.shapeFactory = new JShapeFactory(meshProvider);

        // 1. Config (First, because others depend on it)
        this.configurationService = new ConfigurationService(this, meshProvider);

        // 2. Items (DI initialization)
        this.itemRegistry = new ItemRegistry(configurationService);
        this.itemRegistry.reload();

        // 3. Natives
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 4. NMS
        setupNMSTools();

        // 5. Jolt & Space Managers
        this.physicsEngine = new PhysicsEngine(this, configurationService);
        this.physicsWorldRegistry = new PhysicsWorldRegistry(this, configurationService, physicsEngine);

        // Body Factory (needs config and shapeFactory)
        this.bodyFactory = new BodyFactory(this, physicsWorldRegistry, configurationService, shapeFactory);

        // 6. Tools (Now initialized with dependencies)
        this.toolRegistry = new ToolRegistry(this, configurationService, physicsWorldRegistry, shapeFactory, bodyFactory);

        // 7. API
        InertiaAPI.setImplementation(new InertiaAPIImpl(this, physicsWorldRegistry, configurationService, shapeFactory));
        InertiaLogger.info("Inertia API registered.");

        // 8. Commands & Listeners
        registerCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    private void registerCommands() {
        this.commandManager = new InertiaCommandManager(this, configurationService);
        this.commandManager.registerCommands(
                configurationService,
                bodyFactory,
                toolRegistry,
                physicsWorldRegistry
        );
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(physicsWorldRegistry), this);
    }

    @Override
    public void onDisable() {
        if (physicsWorldRegistry != null) physicsWorldRegistry.shutdown();
        if (physicsEngine != null) physicsEngine.shutdown();
        InertiaLogger.info("Inertia has been disabled.");
    }

    public void reload() {
        if (configurationService != null) configurationService.reloadAsync();
        InertiaLogger.info("Inertia configuration reloaded.");
    }

    private boolean setupNativeLibraries() {
        try {
            this.libraryLoader = new LibraryLoader();
            String precisionStr = this.getConfig().getString("physics.precision", "SP");
            Precision precision = "DP".equalsIgnoreCase(precisionStr) ? Precision.DP : Precision.SP;
            InertiaLogger.info("Jolt Precision set to: " + precision);
            this.libraryLoader.init(this, precision);
            return true;
        } catch (LibraryLoader.JoltNativeException e) {
            InertiaLogger.error("Critical error loading Jolt Natives", e);
            return false;
        }
    }

    private void setupNMSTools() {
        this.joltTools = JoltToolsInit.get();
        this.playerTools = PlayerToolsInit.get();
        this.renderFactory = RenderFactoryInit.get(this.itemRegistry);
    }

    public static InertiaPlugin getInstance() { return instance; }
    public PlayerTools getPlayerTools() { return playerTools; }
    public JoltTools getJoltTools() { return joltTools; }
    public RenderFactory getRenderFactory() { return renderFactory; }
    public JShapeFactory getShapeFactory() { return shapeFactory; }
    public BodyFactory getBodyFactory() { return bodyFactory; }
    public BlockBenchMeshProvider getMeshProvider() { return meshProvider; }

    // Геттеры для DI
    public ConfigurationService getConfigManager() { return configurationService; }
    public PhysicsWorldRegistry getSpaceManager() { return physicsWorldRegistry; }
    public ToolRegistry getToolManager() { return toolRegistry; }
    public PhysicsEngine getJoltManager() { return physicsEngine; }
}