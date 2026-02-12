package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.CloudModule;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;

import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AdminCommands extends CloudModule {

    private final PhysicsWorldRegistry worldRegistry;
    private final PhysicsMetricsService metricsService;

    public AdminCommands(CommandManager<CommandSender> manager,
                         ConfigurationService config,
                         PhysicsWorldRegistry worldRegistry,
                         PhysicsMetricsService metricsService) {
        super(manager, config);
        this.worldRegistry = worldRegistry;
        this.metricsService = metricsService;
    }

    @Override
    public void register() {
        var adminRoot = rootBuilder().literal("admin").permission("inertia.command.admin");

        // Command: /inertia admin pause [true/false]
        manager.command(adminRoot
                .literal("pause")
                .permission("inertia.admin.pause")
                .optional("state", BooleanParser.booleanParser())
                .handler(ctx -> {
                    boolean state;
                    // Если аргумент не указан, переключаем состояние первого найденного мира (упрощение для UX)
                    // или берем глобальный флаг, если бы он был.
                    // В текущей архитектуре пауза локальна для мира, но команда admin pause обычно глобальная.
                    // Реализуем логику: если не указано, то тогл, если указано - сет.
                    
                    // Для простоты возьмем состояние первого мира, чтобы определить текущий тогл
                    boolean isAnyPaused = worldRegistry.getAllSpaces().stream()
                            .anyMatch(IPhysicsWorld::isSimulationPaused);
                    
                    if (ctx.contains("state")) {
                        state = ctx.get("state");
                    } else {
                        state = !isAnyPaused;
                    }

                    int count = 0;
                    for (IPhysicsWorld world : worldRegistry.getAllSpaces()) {
                        world.setSimulationPaused(state);
                        count++;
                    }

                    send(ctx.sender(), MessageKey.ADMIN_SIMULATION_PAUSED, 
                            "{state}", state ? "PAUSED" : "RESUMED", 
                            "{count}", String.valueOf(count));
                })
        );

        // Command: /inertia admin effective-config
        manager.command(adminRoot
                .literal("effective-config")
                .permission("inertia.admin.stats")
                .handler(ctx -> {
                    var effective = config.getAppliedThreadingConfig();
                    if (effective == null) {
                        ctx.sender().sendMessage("§cEffective config is not available yet.");
                        return;
                    }
                    ctx.sender().sendMessage("§6[Inertia] Effective performance.threading:");
                    ctx.sender().sendMessage("§7physics.worldThreads=§f" + effective.physicsWorldThreads()
                            + " §8| §7physics.taskBudgetMs=§f" + effective.physicsTaskBudgetMs()
                            + " §8| §7physics.snapshotQueueMode=§f" + effective.snapshotQueueMode());
                    ctx.sender().sendMessage("§7network.computeThreads=§f" + effective.networkComputeThreads()
                            + " §8| §7network.flushBudgetNanos=§f" + effective.networkFlushBudgetNanos()
                            + " §8| §7network.maxBytesPerTick=§f" + effective.networkMaxBytesPerTick());
                    ctx.sender().sendMessage("§7terrain.captureBudgetMs=§f" + effective.terrainCaptureBudgetMs()
                            + " §8| §7terrain.generateWorkers=§f" + effective.terrainGenerateWorkers()
                            + " §8| §7terrain.maxInFlight=§f" + effective.terrainMaxInFlight());
                })
        );



        // Command: /inertia admin terrain-cache pregen <world> [radius] [all] [centerX] [centerZ] [force]
        manager.command(adminRoot
                .literal("terrain-cache")
                .literal("pregen")
                .permission("inertia.admin.terrain-cache")
                .required("world", StringParser.stringParser(), org.incendo.cloud.suggestion.SuggestionProvider.blockingStrings((c, i) ->
                        worldRegistry.getAllSpaces().stream().map(space -> space.getWorldBukkit().getName()).toList()))
                .optional("radius", IntegerParser.integerParser(0))
                .optional("all", BooleanParser.booleanParser())
                .optional("centerX", IntegerParser.integerParser())
                .optional("centerZ", IntegerParser.integerParser())
                .optional("force", BooleanParser.booleanParser())
                .handler(ctx -> {
                    String worldName = ctx.get("world");
                    PhysicsWorld targetWorld = worldRegistry.getAllSpaces().stream()
                            .filter(space -> space.getWorldBukkit().getName().equalsIgnoreCase(worldName))
                            .findFirst()
                            .orElse(null);

                    if (targetWorld == null) {
                        ctx.sender().sendMessage("§c[Inertia] World not found in physics registry: " + worldName);
                        return;
                    }

                    TerrainAdapter adapter = targetWorld.getTerrainAdapter();
                    if (adapter == null) {
                        ctx.sender().sendMessage("§c[Inertia] Terrain adapter is not enabled for world: " + worldName);
                        return;
                    }

                    boolean all = ctx.getOrDefault("all", false);
                    int radius = Math.max(0, ctx.getOrDefault("radius", 0));
                    boolean force = ctx.getOrDefault("force", false);

                    int defaultChunkX;
                    int defaultChunkZ;
                    if (ctx.sender() instanceof Player player) {
                        defaultChunkX = player.getLocation().getBlockX() >> 4;
                        defaultChunkZ = player.getLocation().getBlockZ() >> 4;
                    } else {
                        defaultChunkX = 0;
                        defaultChunkZ = 0;
                    }

                    int centerChunkX = ctx.getOrDefault("centerX", defaultChunkX);
                    int centerChunkZ = ctx.getOrDefault("centerZ", defaultChunkZ);

                    TerrainAdapter.OfflineGenerationRequest request = new TerrainAdapter.OfflineGenerationRequest(
                            centerChunkX,
                            centerChunkZ,
                            radius,
                            all,
                            force
                    );

                    ctx.sender().sendMessage("§6[Inertia] Starting offline terrain cache pre-generation for world §f" + targetWorld.getWorldBukkit().getName()
                            + "§6, mode=" + (all ? "WORLD_BOUNDS" : "RADIUS")
                            + ", radius=" + radius
                            + ", center=" + centerChunkX + "," + centerChunkZ
                            + ", force=" + force + "...");

                    adapter.generateOffline(request).whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(com.ladakx.inertia.core.InertiaPlugin.getInstance(), () -> {
                        if (throwable != null) {
                            ctx.sender().sendMessage("§c[Inertia] Offline generation failed: " + throwable.getMessage());
                            return;
                        }
                        if (result == null || !result.supported()) {
                            ctx.sender().sendMessage("§c[Inertia] Offline generation is not supported by current terrain adapter.");
                            return;
                        }
                        ctx.sender().sendMessage("§a[Inertia] Offline generation done. Requested=" + result.requestedChunks()
                                + ", generated=" + result.generatedChunks()
                                + ", cache-hit=" + result.skippedFromCache()
                                + ", failed=" + result.failedChunks()
                                + ", durationMs=" + result.durationMillis());
                    }));
                })
        );

        // Command: /inertia admin stats
        manager.command(adminRoot
                .literal("stats")
                .permission("inertia.admin.stats")
                .handler(ctx -> {
                    double avg1s = metricsService.getAverageMspt1s();
                    double avg5s = metricsService.getAverageMspt5s();
                    double avg1m = metricsService.getAverageMspt1m();
                    double peak1s = metricsService.getPeakMspt1s();
                    
                    int activeBodies = metricsService.getActiveBodyCount();
                    int staticBodies = metricsService.getStaticBodyCount();
                    
                    // Форматируем числа
                    String fmtAvg = String.format(Locale.ROOT, "%.2f / %.2f / %.2f", avg1s, avg5s, avg1m);
                    String fmtPeak = String.format(Locale.ROOT, "%.2f", peak1s);
                    String recurringByCategory = String.format(Locale.ROOT, "C:%.2f / N:%.2f / B:%.2f",
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.CRITICAL),
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.NORMAL),
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.BACKGROUND)
                    );
                    String skippedByCategory = String.format(Locale.ROOT, "C:%d / N:%d / B:%d",
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.CRITICAL),
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.NORMAL),
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.BACKGROUND)
                    );

                    send(ctx.sender(), MessageKey.ADMIN_STATS_HEADER);
                    send(ctx.sender(), MessageKey.ADMIN_STATS_PERFORMANCE, "{avg}", fmtAvg, "{peak}", fmtPeak);
                    send(ctx.sender(), MessageKey.ADMIN_STATS_BODIES, "{dynamic}", String.valueOf(activeBodies), "{static}", String.valueOf(staticBodies));
                    send(ctx.sender(), MessageKey.ADMIN_STATS_TASKS,
                            "{oneTime}", String.format(Locale.ROOT, "%.2f", metricsService.getOneTimeExecutionMs()),
                            "{recurringTotal}", String.format(Locale.ROOT, "%.2f", metricsService.getRecurringExecutionMsTotal()),
                            "{recurringByCategory}", recurringByCategory,
                            "{skipped}", skippedByCategory,
                            "{queueOneTime}", String.valueOf(metricsService.getOneTimeQueueDepth()),
                            "{queueRecurring}", String.valueOf(metricsService.getRecurringQueueDepth()),
                            "{snapDropped}", String.valueOf(metricsService.getDroppedSnapshots()),
                            "{snapOverwritten}", String.valueOf(metricsService.getOverwrittenSnapshots())
                    );
                    send(ctx.sender(), MessageKey.ADMIN_STATS_FOOTER);
                })
        );

        manager.command(adminRoot
                .literal("terrain")
                .literal("regenerate")
                .permission("inertia.admin.terrain-cache")
                .required("radius", IntegerParser.integerParser(0))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) {
                        return;
                    }
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) {
                        return;
                    }
                    int radius = ctx.get("radius");
                    executeTerrainRegenerateRadius(player, radius);
                })
        );
    }

    private void executeTerrainRegenerateRadius(Player player, int radius) {
        Objects.requireNonNull(player, "player");
        PhysicsWorld physicsWorld = worldRegistry.getSpace(player.getWorld());
        if (physicsWorld == null) {
            return;
        }
        TerrainAdapter terrainAdapter = physicsWorld.getTerrainAdapter();
        if (terrainAdapter == null) {
            return;
        }

        Chunk centerChunk = player.getLocation().getChunk();
        int centerX = centerChunk.getX();
        int centerZ = centerChunk.getZ();
        int radiusSquared = radius * radius;
        int regenerated = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radiusSquared) {
                    continue;
                }

                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (!player.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    player.getWorld().loadChunk(chunkX, chunkZ, true);
                }
                physicsWorld.onChunkChange(chunkX, chunkZ);
                regenerated++;
            }
        }

        send(player, MessageKey.TERRAIN_REGENERATE_RADIUS_SUCCESS,
                "{count}", String.valueOf(regenerated),
                "{radius}", String.valueOf(radius));
    }
    private List<String> booleanSuggestions() {
        return List.of("true", "false");
    }

    private List<String> radiusSuggestions() {
        return List.of("0", "4", "8", "16", "32", "64", "128");
    }

    private List<String> centerXSuggestions(CommandSender sender) {
        if (sender instanceof Player player) {
            return List.of(String.valueOf(player.getLocation().getBlockX() >> 4));
        }
        return List.of("0");
    }

    private List<String> centerZSuggestions(CommandSender sender) {
        if (sender instanceof Player player) {
            return List.of(String.valueOf(player.getLocation().getBlockZ() >> 4));
        }
        return List.of("0");
    }

}
