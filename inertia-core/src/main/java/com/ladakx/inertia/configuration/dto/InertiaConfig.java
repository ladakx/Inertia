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

        public PhysicsSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.precision = Precision.SP;
                this.workerThreads = 2;
                this.CHUNK_CACHE = new ChunkCacheSettings(null, root);
                return;
            }

            // Parse Precision from String "DP" or "SP"
            String precStr = section.getString("precision", "SP");
            this.precision = "DP".equalsIgnoreCase(precStr) ? Precision.DP : Precision.SP;

            this.workerThreads = section.getInt("worker-threads", 2);
            this.CHUNK_CACHE = new ChunkCacheSettings(section.getConfigurationSection("chunk-cache"), root);
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

            public NetworkEntityTrackerSettings(ConfigurationSection section, FileConfiguration root) {
                // Defaults are aligned with previous hardcoded values in NetworkEntityTracker
                double defaultPosThreshold = 0.01; // blocks
                double defaultRotThresholdDot = 0.3; // quaternion dot (abs)

                double posThreshold = section != null ? section.getDouble("pos-threshold", defaultPosThreshold) : defaultPosThreshold;
                if (posThreshold < 0) posThreshold = 0;
                this.posThresholdSq = (float) (posThreshold * posThreshold);

                double rotDot = section != null ? section.getDouble("rot-threshold-dot", defaultRotThresholdDot) : defaultRotThresholdDot;
                if (rotDot < 0) rotDot = 0;
                if (rotDot > 1) rotDot = 1;
                this.rotThresholdDot = (float) rotDot;
            }
        }
    }
}
