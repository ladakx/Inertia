package com.ladakx.inertia.configuration.dto;

import com.ladakx.inertia.infrastructure.nativelib.Precision;
import com.ladakx.inertia.physics.world.loop.PhysicsLoop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable Data Object representing the config.yml file.
 * Updated to match the new YAML structure (kebab-case) and include default fallback logic.
 */
public class InertiaConfig {

    public final GeneralSettings GENERAL;
    public final PhysicsSettings PHYSICS;
    public final RenderingSettings RENDERING;
    public final PerformanceSettings PERFORMANCE;

    public InertiaConfig(FileConfiguration cfg) {
        this.GENERAL = new GeneralSettings(cfg.getConfigurationSection("general"), cfg);
        this.PHYSICS = new PhysicsSettings(cfg.getConfigurationSection("physics"), cfg);
        this.RENDERING = new RenderingSettings(cfg.getConfigurationSection("rendering"), cfg);
        this.PERFORMANCE = new PerformanceSettings(cfg.getConfigurationSection("performance"), this.PHYSICS);
    }

    public static class PerformanceSettings {
        public final ThreadingSettings THREADING;

        public PerformanceSettings(ConfigurationSection section, PhysicsSettings physicsSettings) {
            this.THREADING = new ThreadingSettings(section != null ? section.getConfigurationSection("threading") : null, physicsSettings);
        }
    }

    public static class ThreadingSettings {
        public final PhysicsThreadingSettings physics;
        public final NetworkThreadingSettings network;
        public final TerrainThreadingSettings terrain;

        public ThreadingSettings(ConfigurationSection section, PhysicsSettings physicsSettings) {
            ConfigurationSection physicsSection = section != null ? section.getConfigurationSection("physics") : null;
            ConfigurationSection networkSection = section != null ? section.getConfigurationSection("network") : null;
            ConfigurationSection terrainSection = section != null ? section.getConfigurationSection("terrain") : null;

            this.physics = new PhysicsThreadingSettings(physicsSection, physicsSettings);
            this.network = new NetworkThreadingSettings(networkSection);
            this.terrain = new TerrainThreadingSettings(terrainSection, physicsSettings);
        }
    }

    public static class PhysicsThreadingSettings {
        public final int worldThreads;
        public final int taskBudgetMs;
        public final PhysicsLoop.SnapshotMode snapshotQueueMode;

        public PhysicsThreadingSettings(ConfigurationSection section, PhysicsSettings physicsSettings) {
            int available = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            int fallbackWorldThreads = physicsSettings != null ? physicsSettings.workerThreads : 2;
            this.worldThreads = clamp(section != null ? section.getInt("world-threads", fallbackWorldThreads) : fallbackWorldThreads, 1, Math.max(1, available * 2));

            long oneTimeBudget = physicsSettings != null ? physicsSettings.TASK_MANAGER.oneTimeTaskBudgetNanos : 4_000_000L;
            long recurringBudget = physicsSettings != null ? physicsSettings.TASK_MANAGER.recurringTaskBudgetNanos : 3_000_000L;
            int fallbackBudgetMs = (int) Math.max(1L, (oneTimeBudget + recurringBudget) / 1_000_000L);
            this.taskBudgetMs = clamp(section != null ? section.getInt("task-budget-ms", fallbackBudgetMs) : fallbackBudgetMs, 1, 100);

            String fallbackMode = physicsSettings != null ? physicsSettings.snapshotMode.name() : PhysicsLoop.SnapshotMode.LATEST.name();
            String modeValue = section != null ? section.getString("snapshot-queue-mode", fallbackMode) : fallbackMode;
            PhysicsLoop.SnapshotMode parsedMode;
            try {
                parsedMode = PhysicsLoop.SnapshotMode.valueOf(modeValue.toUpperCase());
            } catch (IllegalArgumentException ex) {
                parsedMode = PhysicsLoop.SnapshotMode.LATEST;
            }
            this.snapshotQueueMode = parsedMode;
        }
    }

    public static class NetworkThreadingSettings {
        public final int computeThreads;
        public final long flushBudgetNanos;
        public final int maxBytesPerTick;

        public NetworkThreadingSettings(ConfigurationSection section) {
            int fallbackThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            this.computeThreads = clamp(section != null ? section.getInt("compute-threads", fallbackThreads) : fallbackThreads, 1, 16);
            this.flushBudgetNanos = clamp(section != null ? section.getLong("flush-budget-nanos", 2_000_000L) : 2_000_000L, 100_000L, 25_000_000L);
            this.maxBytesPerTick = clamp(section != null ? section.getInt("max-bytes-per-tick", 147_456) : 147_456, 1_024, 4_194_304);
        }
    }

    public static class TerrainThreadingSettings {
        public final int captureBudgetMs;
        public final int generateWorkers;
        public final int maxInFlight;

        public TerrainThreadingSettings(ConfigurationSection section, PhysicsSettings physicsSettings) {
            int fallbackCaptureBudgetMs = 2;
            int fallbackWorkers = physicsSettings != null ? physicsSettings.workerThreads : 2;
            int fallbackInFlight = physicsSettings != null ? physicsSettings.TERRAIN_GENERATION.maxGenerateJobsInFlight : 3;

            this.captureBudgetMs = clamp(section != null ? section.getInt("capture-budget-ms", fallbackCaptureBudgetMs) : fallbackCaptureBudgetMs, 0, 25);
            this.generateWorkers = clamp(section != null ? section.getInt("generate-workers", fallbackWorkers) : fallbackWorkers, 1, 16);
            this.maxInFlight = clamp(section != null ? section.getInt("max-in-flight", fallbackInFlight) : fallbackInFlight, 1, 64);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==========================================
    // General Settings
    // ==========================================
    public static class GeneralSettings {
        public final String lang;
        public final DebugSettings DEBUG;

        public GeneralSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.lang = "en";
                this.DEBUG = new DebugSettings(null, root);
                return;
            }
            this.lang = section.getString("lang", "en");
            this.DEBUG = new DebugSettings(section.getConfigurationSection("debug"), root);
        }

        public static class DebugSettings {

            public boolean consoleDebug = true;
            public final int hitboxDefaultRange;
            public final int hitboxMaxRange;
            public final int hitboxRenderIntervalTicks;

            public DebugSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.hitboxDefaultRange = 20;
                    this.hitboxMaxRange = 100;
                    this.hitboxRenderIntervalTicks = 2;
                    return;
                }

                this.consoleDebug = section.getBoolean("console", true);
                ConfigurationSection hitboxes = section.getConfigurationSection("hitboxes");
                this.hitboxDefaultRange = hitboxes != null ? hitboxes.getInt("default-range", 20) : 20;
                this.hitboxMaxRange = hitboxes != null ? hitboxes.getInt("max-range", 100) : 100;
                this.hitboxRenderIntervalTicks = hitboxes != null ? hitboxes.getInt("render-interval-ticks", 2) : 2;
            }
        }
    }

    // ==========================================
    // Physics Settings
    // ==========================================
    public static class PhysicsSettings {
        public final Precision precision;
        public final int workerThreads;
        public final ChunkCacheSettings CHUNK_CACHE;
        public final TerrainGenerationSettings TERRAIN_GENERATION;
        public final MassSpawnSettings MASS_SPAWN;
        public final TaskManagerSettings TASK_MANAGER;
        public final PhysicsLoop.SnapshotMode snapshotMode;

        public PhysicsSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.precision = Precision.SP;
                this.workerThreads = 2;
                this.CHUNK_CACHE = new ChunkCacheSettings(null, root);
                this.TERRAIN_GENERATION = new TerrainGenerationSettings(null, root);
                this.MASS_SPAWN = new MassSpawnSettings(null, root);
                this.TASK_MANAGER = new TaskManagerSettings(null, root);
                this.snapshotMode = PhysicsLoop.SnapshotMode.LATEST;
                return;
            }

            // Parse Precision from String "DP" or "SP"
            String precStr = section.getString("precision", "SP");
            this.precision = "DP".equalsIgnoreCase(precStr) ? Precision.DP : Precision.SP;

            this.workerThreads = section.getInt("worker-threads", 2);
            this.CHUNK_CACHE = new ChunkCacheSettings(section.getConfigurationSection("chunk-cache"), root);
            this.TERRAIN_GENERATION = new TerrainGenerationSettings(section.getConfigurationSection("terrain-generation"), root);
            this.MASS_SPAWN = new MassSpawnSettings(section.getConfigurationSection("mass-spawn"), root);
            this.TASK_MANAGER = new TaskManagerSettings(section.getConfigurationSection("task-manager"), root);

            String modeValue = section.getString("snapshot-mode", "LATEST");
            PhysicsLoop.SnapshotMode parsedMode;
            try {
                parsedMode = PhysicsLoop.SnapshotMode.valueOf(modeValue.toUpperCase());
            } catch (IllegalArgumentException ex) {
                parsedMode = PhysicsLoop.SnapshotMode.LATEST;
            }
            this.snapshotMode = parsedMode;
        }

        public static class TaskManagerSettings {
            public final int maxOneTimeTasksPerTick;
            public final long oneTimeTaskBudgetNanos;
            public final long recurringTaskBudgetNanos;

            public TaskManagerSettings(ConfigurationSection section, FileConfiguration root) {
                this.maxOneTimeTasksPerTick = section != null
                        ? Math.max(1, section.getInt("max-one-time-tasks-per-tick", 50))
                        : 50;

                long oneTimeBudget = section != null
                        ? section.getLong("one-time-budget-nanos", 4_000_000L)
                        : 4_000_000L;
                this.oneTimeTaskBudgetNanos = Math.max(100_000L, oneTimeBudget);

                long recurringBudget = section != null
                        ? section.getLong("recurring-budget-nanos", 3_000_000L)
                        : 3_000_000L;
                this.recurringTaskBudgetNanos = Math.max(100_000L, recurringBudget);
            }
        }

        public static class TerrainGenerationSettings {
            public final int maxCapturePerTick;
            public final int maxGenerateJobsInFlight;

            public TerrainGenerationSettings(ConfigurationSection section, FileConfiguration root) {
                this.maxCapturePerTick = section != null
                        ? Math.max(1, section.getInt("max-capture-per-tick", 4))
                        : 4;
                this.maxGenerateJobsInFlight = section != null
                        ? Math.max(1, section.getInt("max-generate-jobs-in-flight", 3))
                        : 3;
            }
        }

        public static class ChunkCacheSettings {
            public final int maxEntries;
            public final int memoryTtlSeconds;
            public final int diskTtlSeconds;

            public ChunkCacheSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.maxEntries = 4096;
                    this.memoryTtlSeconds = 900;
                    this.diskTtlSeconds = 7200;
                    return;
                }

                this.maxEntries = section.getInt("max-entries", 4096);
                int legacyTtl = section.getInt("ttl-seconds", 900);
                this.memoryTtlSeconds = section.getInt("memory-ttl-seconds", legacyTtl);
                this.diskTtlSeconds = section.getInt("disk-ttl-seconds", Math.max(legacyTtl, 7200));
            }
        }

        public static class MassSpawnSettings {
            public final int minBudgetPerTick;
            public final int baseBudgetPerTick;
            public final int maxBudgetPerTick;
            public final int warmupBudgetPerTick;
            public final int warmupTicks;
            public final int budgetIncreaseStep;
            public final int budgetDecreaseStep;
            public final double stableTpsThreshold;
            public final int stableTicksToIncreaseBudget;
            public final int maxConcurrentJobsPerWorld;
            public final int maxConcurrentJobsPerPlayer;
            public final int maxSpawnsPerJobPerTick;

            public MassSpawnSettings(ConfigurationSection section, FileConfiguration root) {
                this.minBudgetPerTick = section != null ? section.getInt("min-budget-per-tick", 50) : 50;
                this.baseBudgetPerTick = section != null ? section.getInt("base-budget-per-tick", 100) : 100;
                this.maxBudgetPerTick = section != null ? section.getInt("max-budget-per-tick", 200) : 200;
                this.warmupBudgetPerTick = section != null ? section.getInt("warmup-budget-per-tick", 50) : 50;
                this.warmupTicks = section != null ? section.getInt("warmup-ticks", 40) : 40;
                this.budgetIncreaseStep = section != null ? section.getInt("budget-increase-step", 10) : 10;
                this.budgetDecreaseStep = section != null ? section.getInt("budget-decrease-step", 20) : 20;
                this.stableTpsThreshold = section != null ? section.getDouble("stable-tps-threshold", 19.2D) : 19.2D;
                this.stableTicksToIncreaseBudget = section != null ? section.getInt("stable-ticks-to-increase-budget", 40) : 40;
                this.maxConcurrentJobsPerWorld = section != null ? section.getInt("max-concurrent-jobs-per-world", 2) : 2;
                this.maxConcurrentJobsPerPlayer = section != null ? section.getInt("max-concurrent-jobs-per-player", 1) : 1;
                this.maxSpawnsPerJobPerTick = section != null ? section.getInt("max-spawns-per-job-per-tick", 200) : 200;
            }
        }
    }

    // ==========================================
    // Rendering Settings
    // ==========================================
    public static class RenderingSettings {
        public final NetworkEntityTrackerSettings NETWORK_ENTITY_TRACKER;

        public RenderingSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.NETWORK_ENTITY_TRACKER = new NetworkEntityTrackerSettings(null, root);
                return;
            }
            this.NETWORK_ENTITY_TRACKER = new NetworkEntityTrackerSettings(section.getConfigurationSection("network-entity-tracker"), root);
        }

        public static class NetworkEntityTrackerSettings {
            public final float posThresholdSq;
            public final float rotThresholdDot;
            public final float nearPosThresholdSq;
            public final float midPosThresholdSq;
            public final float farPosThresholdSq;
            public final float nearRotThresholdDot;
            public final float midRotThresholdDot;
            public final float farRotThresholdDot;
            public final float midDistanceSq;
            public final float farDistanceSq;
            public final int midUpdateIntervalTicks;
            public final int farUpdateIntervalTicks;
            public final boolean farAllowMetadataUpdates;
            public final int maxVisibilityUpdatesPerPlayerPerTick;
            public final int maxTransformChecksPerPlayerPerTick;
            public final int fullRecalcIntervalTicks;
            public final int maxPacketsPerPlayerPerTick;
            public final int destroyBacklogThreshold;
            public final int destroyDrainExtraPacketsPerPlayerPerTick;
            public final int maxBytesPerPlayerPerTick;
            public final long maxWorkNanosPerTick;
            public final double secondaryBudgetMinScale;
            public final double adaptiveTpsSoftThreshold;
            public final double adaptiveTpsHardThreshold;
            public final int adaptivePingSoftThresholdMs;
            public final int adaptivePingHardThresholdMs;

            public NetworkEntityTrackerSettings(ConfigurationSection section, FileConfiguration root) {
                // Defaults are aligned with previous hardcoded values in NetworkEntityTracker
                double defaultPosThreshold = 0.01; // blocks
                double defaultRotThresholdDot = 0.3; // quaternion dot (abs)

                double posThreshold = section != null ? section.getDouble("pos-threshold", defaultPosThreshold) : defaultPosThreshold;
                if (posThreshold < 0) posThreshold = 0;
                this.posThresholdSq = (float) (posThreshold * posThreshold);
                this.nearPosThresholdSq = this.posThresholdSq;

                double defaultMidPosThreshold = 0.04;
                double midPosThreshold = section != null ? section.getDouble("mid-pos-threshold", defaultMidPosThreshold) : defaultMidPosThreshold;
                if (midPosThreshold < 0) midPosThreshold = 0;
                this.midPosThresholdSq = (float) (midPosThreshold * midPosThreshold);

                double defaultFarPosThreshold = 0.09;
                double farPosThreshold = section != null ? section.getDouble("far-pos-threshold", defaultFarPosThreshold) : defaultFarPosThreshold;
                if (farPosThreshold < 0) farPosThreshold = 0;
                this.farPosThresholdSq = (float) (farPosThreshold * farPosThreshold);

                double rotDot = section != null ? section.getDouble("rot-threshold-dot", defaultRotThresholdDot) : defaultRotThresholdDot;
                if (rotDot < 0) rotDot = 0;
                if (rotDot > 1) rotDot = 1;
                this.rotThresholdDot = (float) rotDot;
                this.nearRotThresholdDot = this.rotThresholdDot;

                double defaultMidRotDot = 0.2;
                double midRotDot = section != null ? section.getDouble("mid-rot-threshold-dot", defaultMidRotDot) : defaultMidRotDot;
                if (midRotDot < 0) midRotDot = 0;
                if (midRotDot > 1) midRotDot = 1;
                this.midRotThresholdDot = (float) midRotDot;

                double defaultFarRotDot = 0.1;
                double farRotDot = section != null ? section.getDouble("far-rot-threshold-dot", defaultFarRotDot) : defaultFarRotDot;
                if (farRotDot < 0) farRotDot = 0;
                if (farRotDot > 1) farRotDot = 1;
                this.farRotThresholdDot = (float) farRotDot;

                double defaultMidDistance = 24.0;
                double midDistance = section != null ? section.getDouble("mid-distance", defaultMidDistance) : defaultMidDistance;
                if (midDistance < 0) midDistance = 0;
                this.midDistanceSq = (float) (midDistance * midDistance);

                double defaultFarDistance = 56.0;
                double farDistance = section != null ? section.getDouble("far-distance", defaultFarDistance) : defaultFarDistance;
                if (farDistance < midDistance) farDistance = midDistance;
                this.farDistanceSq = (float) (farDistance * farDistance);

                int defaultMidUpdateIntervalTicks = 2;
                int configuredMidUpdateIntervalTicks = section != null
                        ? section.getInt("mid-update-interval-ticks", defaultMidUpdateIntervalTicks)
                        : defaultMidUpdateIntervalTicks;
                this.midUpdateIntervalTicks = Math.max(1, configuredMidUpdateIntervalTicks);

                int defaultFarUpdateIntervalTicks = 4;
                int configuredFarUpdateIntervalTicks = section != null
                        ? section.getInt("far-update-interval-ticks", defaultFarUpdateIntervalTicks)
                        : defaultFarUpdateIntervalTicks;
                this.farUpdateIntervalTicks = Math.max(1, configuredFarUpdateIntervalTicks);

                this.farAllowMetadataUpdates = section != null && section.getBoolean("far-allow-metadata-updates", false);

                int defaultMaxUpdatesPerTick = 256;
                int configuredMaxUpdatesPerTick = section != null
                        ? section.getInt("max-visibility-updates-per-player-per-tick", defaultMaxUpdatesPerTick)
                        : defaultMaxUpdatesPerTick;
                this.maxVisibilityUpdatesPerPlayerPerTick = Math.max(1, configuredMaxUpdatesPerTick);

                int defaultMaxTransformChecksPerTick = 256;
                int configuredMaxTransformChecksPerTick = section != null
                        ? section.getInt("max-transform-checks-per-player-per-tick", defaultMaxTransformChecksPerTick)
                        : defaultMaxTransformChecksPerTick;
                this.maxTransformChecksPerPlayerPerTick = Math.max(1, configuredMaxTransformChecksPerTick);

                int defaultFullRecalcIntervalTicks = 20;
                int configuredFullRecalcIntervalTicks = section != null
                        ? section.getInt("full-recalc-interval-ticks", defaultFullRecalcIntervalTicks)
                        : defaultFullRecalcIntervalTicks;
                this.fullRecalcIntervalTicks = Math.max(1, configuredFullRecalcIntervalTicks);

                int defaultMaxPacketsPerPlayerPerTick = 256;
                int configuredMaxPacketsPerPlayerPerTick = section != null
                        ? section.getInt("max-packets-per-player-per-tick", defaultMaxPacketsPerPlayerPerTick)
                        : defaultMaxPacketsPerPlayerPerTick;
                this.maxPacketsPerPlayerPerTick = Math.max(1, configuredMaxPacketsPerPlayerPerTick);

                int defaultDestroyBacklogThreshold = 512;
                int configuredDestroyBacklogThreshold = section != null
                        ? section.getInt("destroy-backlog-threshold", defaultDestroyBacklogThreshold)
                        : defaultDestroyBacklogThreshold;
                this.destroyBacklogThreshold = Math.max(1, configuredDestroyBacklogThreshold);

                int defaultDestroyDrainExtraPacketsPerPlayerPerTick = 128;
                int configuredDestroyDrainExtraPacketsPerPlayerPerTick = section != null
                        ? section.getInt("destroy-drain-extra-packets-per-player-per-tick", defaultDestroyDrainExtraPacketsPerPlayerPerTick)
                        : defaultDestroyDrainExtraPacketsPerPlayerPerTick;
                this.destroyDrainExtraPacketsPerPlayerPerTick = Math.max(0, configuredDestroyDrainExtraPacketsPerPlayerPerTick);

                int defaultMaxBytesPerPlayerPerTick = 98304;
                int configuredMaxBytesPerPlayerPerTick = section != null
                        ? section.getInt("max-bytes-per-player-per-tick", defaultMaxBytesPerPlayerPerTick)
                        : defaultMaxBytesPerPlayerPerTick;
                this.maxBytesPerPlayerPerTick = Math.max(1, configuredMaxBytesPerPlayerPerTick);

                long defaultMaxWorkNanosPerTick = 2_000_000L;
                long configuredMaxWorkNanosPerTick = section != null
                        ? section.getLong("max-work-nanos-per-tick", defaultMaxWorkNanosPerTick)
                        : defaultMaxWorkNanosPerTick;
                this.maxWorkNanosPerTick = Math.max(100_000L, configuredMaxWorkNanosPerTick);

                double defaultSecondaryBudgetMinScale = 0.25D;
                double configuredSecondaryBudgetMinScale = section != null
                        ? section.getDouble("secondary-budget-min-scale", defaultSecondaryBudgetMinScale)
                        : defaultSecondaryBudgetMinScale;
                this.secondaryBudgetMinScale = Math.max(0.05D, Math.min(1.0D, configuredSecondaryBudgetMinScale));

                double defaultAdaptiveTpsSoftThreshold = 19.2D;
                this.adaptiveTpsSoftThreshold = section != null
                        ? section.getDouble("adaptive-tps-soft-threshold", defaultAdaptiveTpsSoftThreshold)
                        : defaultAdaptiveTpsSoftThreshold;

                double defaultAdaptiveTpsHardThreshold = 17.0D;
                double configuredAdaptiveTpsHardThreshold = section != null
                        ? section.getDouble("adaptive-tps-hard-threshold", defaultAdaptiveTpsHardThreshold)
                        : defaultAdaptiveTpsHardThreshold;
                this.adaptiveTpsHardThreshold = Math.max(1.0D, Math.min(configuredAdaptiveTpsHardThreshold, this.adaptiveTpsSoftThreshold));

                int defaultAdaptivePingSoftThresholdMs = 120;
                int configuredAdaptivePingSoftThresholdMs = section != null
                        ? section.getInt("adaptive-ping-soft-threshold-ms", defaultAdaptivePingSoftThresholdMs)
                        : defaultAdaptivePingSoftThresholdMs;
                this.adaptivePingSoftThresholdMs = Math.max(1, configuredAdaptivePingSoftThresholdMs);

                int defaultAdaptivePingHardThresholdMs = 220;
                int configuredAdaptivePingHardThresholdMs = section != null
                        ? section.getInt("adaptive-ping-hard-threshold-ms", defaultAdaptivePingHardThresholdMs)
                        : defaultAdaptivePingHardThresholdMs;
                this.adaptivePingHardThresholdMs = Math.max(this.adaptivePingSoftThresholdMs, configuredAdaptivePingHardThresholdMs);
            }
        }
    }
}
