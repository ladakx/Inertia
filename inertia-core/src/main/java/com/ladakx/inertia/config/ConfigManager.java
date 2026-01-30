package com.ladakx.inertia.config;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.*;
import com.ladakx.inertia.config.message.MessageManager;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.utils.mesh.BlockBenchMeshProvider;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

public class ConfigManager {

    private final InertiaPlugin plugin;
    private final PhysicsBodyRegistry physicsBodyRegistry;
    private final MessageManager messageManager;

    // Volatile is important for thread visibility if configs are swapped
    private volatile InertiaConfig inertiaConfig;
    private volatile BodiesConfig bodiesConfig;
    private volatile RenderConfig renderConfig;
    private volatile WorldsConfig worldsConfig;

    private BodiesFile bodiesFile;
    private ItemsFile itemsFile;
    private RenderFile renderFile;
    private WorldsFile worldsFile;
    private MessagesFile messagesFile;

    public ConfigManager(InertiaPlugin plugin) {
        this.plugin = plugin;
        this.physicsBodyRegistry = new PhysicsBodyRegistry();
        this.messageManager = new MessageManager();
        // Initial load in constructor is usually synchronous to ensure plugin starts in a valid state
        // But internal logic should be safe.
        loadSync();
    }

    /**
     * Synchronous load (used for onEnable).
     */
    private void loadSync() {
        try {
            InertiaLogger.info("Loading configurations synchronously...");
            performLoadIO();
            applyConfiguration();
            InertiaLogger.info("Configurations loaded.");
        } catch (Exception e) {
            InertiaLogger.error("Failed to load configurations (Sync)", e);
        }
    }

    /**
     * Asynchronous reload (used for /inertia reload).
     */
    public CompletableFuture<Void> reloadAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                InertiaLogger.info("Loading configurations asynchronously...");
                performLoadIO();
            } catch (Exception e) {
                throw new RuntimeException("Async IO failed", e);
            }
        }).thenRunAsync(() -> {
            // Apply logic must run on Main Thread because it might touch Bukkit API or update global state
            try {
                applyConfiguration();
                InertiaLogger.info("Configurations reloaded successfully.");
            } catch (Exception e) {
                InertiaLogger.error("Failed to apply configurations", e);
            }
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * IO-heavy operations: reading files, parsing YAML, parsing Meshes.
     */
    private void performLoadIO() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.inertiaConfig = new InertiaConfig(plugin.getConfig());

        this.itemsFile = new ItemsFile(plugin);
        this.bodiesFile = new BodiesFile(plugin);
        this.bodiesConfig = new BodiesConfig(bodiesFile.getConfig());

        this.renderFile = new RenderFile(plugin);
        this.renderConfig = new RenderConfig(renderFile.getConfig());

        this.worldsFile = new WorldsFile(plugin);
        this.worldsConfig = new WorldsConfig(worldsFile.getConfig());

        this.messagesFile = new MessagesFile(plugin);

        // Preload Meshes here (Heavy IO)
        // We need to scan bodiesConfig to find required meshes
        preloadMeshes(bodiesConfig);
    }

    /**
     * Logic to apply loaded configs to the runtime.
     * Should be fast, but updates state visible to the server.
     */
    private void applyConfiguration() {
        // Оновлюємо налаштування дебагу в логері
        boolean debug = inertiaConfig.GENERAL.DEBUG.consoleDebug;
        InertiaLogger.setDebugMode(debug);

        // Reload messages
        this.messageManager.reload(messagesFile.getConfig());

        // Reload registry (requires items to be loaded? No, registry uses strings for renders)
        physicsBodyRegistry.reload(bodiesConfig, renderConfig);
    }

    private void preloadMeshes(BodiesConfig bodies) {
        var provider = JShapeFactory.getMeshProvider();
        if (provider.isPresent() && provider.get() instanceof BlockBenchMeshProvider meshProvider) {
            //meshProvider.clearCache(); // Clear old cache
            // Scan bodies for 'mesh=' and load them
            // This logic requires BodiesConfig to expose raw shapes or iterate definitions
            // For now, we assume simple lazy loading or implement a scanner if needed.
            // But to fully comply with "No IO in Main", we should iterate bodiesConfig.all() here
            // and trigger loadMesh() for any convex_hull shape.
        }
    }

    // Getters...
    public BodiesConfig getBodiesConfig() { return bodiesConfig; }
    public RenderConfig getRenderConfig() { return renderConfig; }
    public WorldsConfig getWorldsConfig() { return worldsConfig; }
    public InertiaConfig getInertiaConfig() { return inertiaConfig; }
    public ItemsFile getItemsFile() { return itemsFile; }
    public PhysicsBodyRegistry getPhysicsBodyRegistry() { return physicsBodyRegistry; }
    public MessageManager getMessageManager() { return messageManager; }
}