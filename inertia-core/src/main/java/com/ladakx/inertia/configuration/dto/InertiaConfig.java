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

    public InertiaConfig(FileConfiguration cfg) {
        this.GENERAL = new GeneralSettings(cfg.getConfigurationSection("general"), cfg);
        this.PHYSICS = new PhysicsSettings(cfg.getConfigurationSection("physics"), cfg);
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
            public final int ttlSeconds;

            public ChunkCacheSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.maxEntries = 4096;
                    this.ttlSeconds = 900;
                    return;
                }

                this.maxEntries = section.getInt("max-entries", 4096);
                this.ttlSeconds = section.getInt("ttl-seconds", 900);
            }
        }
    }
}
