package com.ladakx.inertia.configuration;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.dto.BodiesConfig;
import com.ladakx.inertia.configuration.dto.BlocksConfig;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.configuration.dto.RenderConfig;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.configuration.files.*;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyDefinition;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.common.mesh.BlockBenchMeshProvider;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigurationService {

    private static final Pattern MESH_PATTERN = Pattern.compile("mesh=([^\\s]+)");
    private final BlockBenchMeshProvider meshProvider;

    private final InertiaPlugin plugin;
    private final PhysicsBodyRegistry physicsBodyRegistry;
    private final MessageManager messageManager;

    // Volatile is important for thread visibility if configs are swapped
    private volatile InertiaConfig inertiaConfig;
    private volatile BodiesConfig bodiesConfig;
    private volatile BlocksConfig blocksConfig;
    private volatile RenderConfig renderConfig;
    private volatile WorldsConfig worldsConfig;
    private volatile AppliedThreadingConfig appliedThreadingConfig;

    private BlocksFile blocksFile;
    private BodiesFile bodiesFile;
    private ItemsFile itemsFile;
    private RenderFile renderFile;
    private WorldsFile worldsFile;
    private MessagesFile messagesFile;

    public ConfigurationService(InertiaPlugin plugin, BlockBenchMeshProvider meshProvider) {
        this.plugin = plugin;
        this.physicsBodyRegistry = new PhysicsBodyRegistry();
        this.messageManager = new MessageManager();
        this.meshProvider = meshProvider;

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
                InertiaLogger.error("Async IO failed during reload", e);
                throw new RuntimeException("Async IO failed", e);
            }
        }).thenRunAsync(() -> {
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
        this.blocksFile = new BlocksFile(plugin);
        this.blocksConfig = new BlocksConfig(blocksFile.getConfig());
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
        this.appliedThreadingConfig = AppliedThreadingConfig.from(inertiaConfig);
    }

    private void preloadMeshes(BodiesConfig bodies) {
        if (meshProvider == null) return;

        meshProvider.clearCache();
        Set<String> meshesToLoad = new HashSet<>();

        // Scan all bodies
        for (BodyDefinition def : bodies.all()) {
            if (def instanceof BlockBodyDefinition b) {
                collectMeshes(b.shapeLines(), meshesToLoad);
            } else if (def instanceof ChainBodyDefinition c) {
                collectMeshes(c.shapeLines(), meshesToLoad);
            } else if (def instanceof RagdollDefinition r) {
                for (var part : r.parts().values()) {
                    if (part.shapeString() != null) {
                        collectMeshes(List.of(part.shapeString()), meshesToLoad);
                    }
                }
            }
        }

        if (!meshesToLoad.isEmpty()) {
            InertiaLogger.info("Preloading " + meshesToLoad.size() + " meshes...");
            for (String meshId : meshesToLoad) {
                meshProvider.loadMesh(meshId);
            }
        }
    }

    private void collectMeshes(List<String> shapeLines, Set<String> accumulator) {
        for (String line : shapeLines) {
            if (line.contains("type=convex_hull")) {
                Matcher m = MESH_PATTERN.matcher(line);
                if (m.find()) {
                    accumulator.add(m.group(1));
                }
            }
        }
    }


    public record AppliedThreadingConfig(
            int physicsWorldThreads,
            int physicsTaskBudgetMs,
            String snapshotQueueMode,
            int networkComputeThreads,
            long networkFlushBudgetNanos,
            int networkMaxBytesPerTick,
            int terrainCaptureBudgetMs,
            int terrainGenerateWorkers,
            int terrainMaxInFlight
    ) {
        static AppliedThreadingConfig from(InertiaConfig config) {
            var t = config.PERFORMANCE.THREADING;
            return new AppliedThreadingConfig(
                    t.physics.worldThreads,
                    t.physics.taskBudgetMs,
                    t.physics.snapshotQueueMode.name(),
                    t.network.computeThreads,
                    t.network.flushBudgetNanos,
                    t.network.maxBytesPerTick,
                    t.terrain.captureBudgetMs,
                    t.terrain.generateWorkers,
                    t.terrain.maxInFlight
            );
        }
    }

    public AppliedThreadingConfig getAppliedThreadingConfig() {
        return appliedThreadingConfig;
    }

    // Getters...
    public BodiesConfig getBodiesConfig() { return bodiesConfig; }
    public BlocksConfig getBlocksConfig() { return blocksConfig; }
    public RenderConfig getRenderConfig() { return renderConfig; }
    public WorldsConfig getWorldsConfig() { return worldsConfig; }
    public InertiaConfig getInertiaConfig() { return inertiaConfig; }
    public ItemsFile getItemsFile() { return itemsFile; }
    public PhysicsBodyRegistry getPhysicsBodyRegistry() { return physicsBodyRegistry; }
    public MessageManager getMessageManager() { return messageManager; }
}
