package com.ladakx.inertia.core;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.service.DebugRenderService;
import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.common.mesh.BlockBenchMeshProvider;
import com.ladakx.inertia.core.impl.InertiaAPIImpl;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.features.commands.InertiaCommandManager;
import com.ladakx.inertia.features.items.ItemRegistry;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.features.ui.BossBarPerformanceMonitor;
import com.ladakx.inertia.physics.engine.PhysicsEngine;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.listeners.BlockChangeListener;
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
import com.ladakx.inertia.features.tools.ToolRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class InertiaPlugin extends JavaPlugin {

    private static InertiaPlugin instance;

    private ConfigurationService configurationService;
    private PhysicsEngine physicsEngine;
    private PhysicsWorldRegistry physicsWorldRegistry;
    private ToolRegistry toolRegistry;
    private ItemRegistry itemRegistry;
    private LibraryLoader libraryLoader;
    private PlayerTools playerTools;
    private JoltTools joltTools;
    private RenderFactory renderFactory;
    private JShapeFactory shapeFactory;
    private BodyFactory bodyFactory;
    private BlockBenchMeshProvider meshProvider;
    private PhysicsManipulationService manipulationService;
    private PhysicsMetricsService metricsService;
    private DebugRenderService debugRenderService;
    private BossBarPerformanceMonitor perfMonitor;
    private ToolDataManager toolDataManager;
    private InertiaCommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        // 1. Сначала загружаем нативные библиотеки, так как остальные сервисы (конфиг, фабрики) зависят от Jolt классов
        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Загружаем конфигурацию (теперь безопасно создавать AaBox и другие Jolt объекты)
        this.configurationService = new ConfigurationService(this, meshProvider);

        this.itemRegistry = new ItemRegistry(configurationService);
        this.itemRegistry.reload();

        this.meshProvider = new BlockBenchMeshProvider(this);
        this.shapeFactory = new JShapeFactory(meshProvider);

        // 2. Инициализируем базовые сервисы
        setupNMSTools(); // Инициализация NMS инструментов (JoltTools, PlayerTools)

        this.metricsService = new PhysicsMetricsService();
        this.physicsEngine = new PhysicsEngine(this, configurationService);
        this.physicsWorldRegistry = new PhysicsWorldRegistry(this, configurationService, physicsEngine, metricsService);
        this.manipulationService = new PhysicsManipulationService();
        this.toolDataManager = new ToolDataManager(this);
        this.bodyFactory = new BodyFactory(this, physicsWorldRegistry, configurationService, shapeFactory);
        this.toolRegistry = new ToolRegistry(this, configurationService, physicsWorldRegistry, shapeFactory, bodyFactory, manipulationService, toolDataManager);
        this.debugRenderService = new DebugRenderService(physicsWorldRegistry, configurationService);
        this.debugRenderService.start();
        this.perfMonitor = new BossBarPerformanceMonitor(this, metricsService, configurationService);

        InertiaAPI.setImplementation(new InertiaAPIImpl(this, physicsWorldRegistry, configurationService, shapeFactory));
        InertiaLogger.info("Inertia API registered.");

        new com.ladakx.inertia.features.integrations.WorldEditIntegration().init();

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
                physicsWorldRegistry,
                metricsService,
                debugRenderService,
                perfMonitor
        );
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new WorldLoadListener(physicsWorldRegistry), this);
        Bukkit.getPluginManager().registerEvents(new BlockChangeListener(physicsWorldRegistry), this);
    }

    @Override
    public void onDisable() {
        if (perfMonitor != null) perfMonitor.stop();
        if (debugRenderService != null) debugRenderService.stop();
        if (physicsWorldRegistry != null) physicsWorldRegistry.shutdown();
        if (physicsEngine != null) physicsEngine.shutdown();
        InertiaLogger.info("Inertia has been disabled.");
    }

    public void reload() {
        if (configurationService != null) {
            configurationService.reloadAsync().thenRun(() -> {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (physicsWorldRegistry != null) {
                        physicsWorldRegistry.reload();
                    }
                    if (itemRegistry != null) {
                        itemRegistry.reload();
                    }
                    InertiaLogger.info("Inertia systems reloaded.");
                });
            });
        }
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
    public ItemRegistry getItemRegistry() { return itemRegistry; }
    public JShapeFactory getShapeFactory() { return shapeFactory; }
    public BodyFactory getBodyFactory() { return bodyFactory; }
    public BlockBenchMeshProvider getMeshProvider() { return meshProvider; }
    public ConfigurationService getConfigManager() { return configurationService; }
    public PhysicsWorldRegistry getSpaceManager() { return physicsWorldRegistry; }
    public ToolRegistry getToolManager() { return toolRegistry; }
    public PhysicsEngine getJoltManager() { return physicsEngine; }
    public PhysicsManipulationService getManipulationService() { return manipulationService; }
    public ToolDataManager getToolDataManager() { return toolDataManager; }
    public PhysicsMetricsService getMetricsService() { return metricsService; }
    public DebugRenderService getDebugRenderService() { return debugRenderService; }
    public BossBarPerformanceMonitor getPerfMonitor() { return perfMonitor; }
}
