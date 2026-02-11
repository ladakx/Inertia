package com.ladakx.inertia.configuration.dto;

import com.ladakx.inertia.infrastructure.nativelib.Precision;
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

    public InertiaConfig(FileConfiguration cfg) {
        this.GENERAL = new GeneralSettings(cfg.getConfigurationSection("general"), cfg);
        this.PHYSICS = new PhysicsSettings(cfg.getConfigurationSection("physics"), cfg);
        this.RENDERING = new RenderingSettings(cfg.getConfigurationSection("rendering"), cfg);
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
        public final MassSpawnSettings MASS_SPAWN;

        public PhysicsSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.precision = Precision.SP;
                this.workerThreads = 2;
                this.CHUNK_CACHE = new ChunkCacheSettings(null, root);
                this.MASS_SPAWN = new MassSpawnSettings(null, root);
                return;
            }

            // Parse Precision from String "DP" or "SP"
            String precStr = section.getString("precision", "SP");
            this.precision = "DP".equalsIgnoreCase(precStr) ? Precision.DP : Precision.SP;

            this.workerThreads = section.getInt("worker-threads", 2);
            this.CHUNK_CACHE = new ChunkCacheSettings(section.getConfigurationSection("chunk-cache"), root);
            this.MASS_SPAWN = new MassSpawnSettings(section.getConfigurationSection("mass-spawn"), root);
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
            public final int fullRecalcIntervalTicks;
            public final int maxPacketsPerPlayerPerTick;
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
