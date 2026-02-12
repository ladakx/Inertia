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
import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import com.ladakx.inertia.infrastructure.nms.network.NetworkManagerInit;
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
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.infrastructure.nms.render.RenderFactoryInit;
import com.ladakx.inertia.features.tools.ToolRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

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

    private NetworkEntityTracker networkEntityTracker;
    private NetworkManager networkManager;

    private BukkitTask globalNetworkTask;

    @Override
    public void onEnable() {
        instance = this;
        InertiaLogger.init(this);
        InertiaLogger.info("Starting Inertia initialization...");

        if (!setupNativeLibraries()) {
            InertiaLogger.error("Failed to initialize Jolt Physics Engine. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.configurationService = new ConfigurationService(this, meshProvider);
        this.itemRegistry = new ItemRegistry(configurationService);
        this.itemRegistry.reload();

        this.meshProvider = new BlockBenchMeshProvider(this);
        this.shapeFactory = new JShapeFactory(meshProvider);

        setupNMSTools();

        this.networkManager = NetworkManagerInit.get();

        // Инициализация сетевого слоя
        com.ladakx.inertia.infrastructure.nms.packet.PacketFactory packetFactory =
                com.ladakx.inertia.infrastructure.nms.packet.PacketFactoryInit.get();

        if (packetFactory == null) {
            InertiaLogger.error("Could not initialize PacketFactory. Rendering will be disabled.");
        }

        this.networkEntityTracker = new NetworkEntityTracker(
                packetFactory,
                configurationService.getInertiaConfig().RENDERING.NETWORK_ENTITY_TRACKER,
                configurationService.getInertiaConfig().PERFORMANCE.THREADING.network
        );

        // Global network tick task (Replaces local tasks in PhysicsWorld)
        this.globalNetworkTask = Bukkit.getScheduler().runTaskTimer(this, this::runGlobalNetworkTick, 1L, 1L);

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

        InertiaAPI.setImplementation(new InertiaAPIImpl(this, physicsWorldRegistry, configurationService, shapeFactory, networkEntityTracker));
        InertiaLogger.info("Inertia API registered.");

        new com.ladakx.inertia.features.integrations.WorldEditIntegration().init();
        registerCommands();
        registerListeners();

        InertiaLogger.info("Inertia has been enabled successfully!");
    }

    private void runGlobalNetworkTick() {
        if (networkEntityTracker != null) {
            // View distance calculation is simplified here for global scope.
            // In a real scenario, this might need per-world settings, but usually server view distance is uniform or handled by tracker per player.
            int viewDistanceBlocks = Bukkit.getViewDistance() * 16;
            double viewDistanceSquared = (double) viewDistanceBlocks * viewDistanceBlocks;
            networkEntityTracker.tick(Bukkit.getOnlinePlayers(), viewDistanceSquared);
        }
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
        // Added network listener if not already there
        Bukkit.getPluginManager().registerEvents(new com.ladakx.inertia.features.listeners.NetworkListener(networkManager), this);
    }

    @Override
    public void onDisable() {
        if (globalNetworkTask != null) {
            globalNetworkTask.cancel();
            globalNetworkTask = null;
        }

        if (perfMonitor != null) perfMonitor.stop();
        if (debugRenderService != null) debugRenderService.stop();

        if (networkManager != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                networkManager.uninject(p);
            }
        }

        if (physicsWorldRegistry != null) physicsWorldRegistry.shutdown();
        if (physicsEngine != null) physicsEngine.shutdown();
        if (bodyFactory != null) bodyFactory.shutdown();

        if (networkEntityTracker != null) networkEntityTracker.clear();

        InertiaLogger.info("Inertia has been disabled.");
    }

    public void reload() {
        if (configurationService == null) {
            return;
        }

        configurationService.reloadAsync()
                .thenCompose(v -> CompletableFuture.runAsync(() -> {
                    // IO-free pre-apply stage (kept async intentionally for safe hot reload staging)
                    configurationService.getAppliedThreadingConfig();
                }))
                .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                    if (itemRegistry != null) {
                        itemRegistry.reload();
                    }
                    if (networkEntityTracker != null) {
                        networkEntityTracker.applySettings(configurationService.getInertiaConfig().RENDERING.NETWORK_ENTITY_TRACKER);
                        networkEntityTracker.applyThreadingSettings(configurationService.getInertiaConfig().PERFORMANCE.THREADING.network);
                    }
                    if (physicsWorldRegistry != null) {
                        physicsWorldRegistry.applyThreadingSettings(configurationService.getInertiaConfig().PERFORMANCE.THREADING);
                    }
                    InertiaLogger.info("Inertia systems reloaded.");
                }));
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
    public NetworkManager getNetworkManager() { return networkManager; }
    public NetworkEntityTracker getNetworkEntityTracker() { return networkEntityTracker; }
}
