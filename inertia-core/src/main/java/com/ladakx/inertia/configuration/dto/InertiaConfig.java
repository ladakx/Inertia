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
            public final String debugPlaceholderBar;
            public final int hitboxDefaultRange;
            public final int hitboxMaxRange;
            public final int hitboxRenderIntervalTicks;

            public DebugSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.debugPlaceholderBar = "Debug info...";
                    this.hitboxDefaultRange = 20;
                    this.hitboxMaxRange = 100;
                    this.hitboxRenderIntervalTicks = 2;
                    return;
                }

                this.consoleDebug = section.getBoolean("console", true);
                this.debugPlaceholderBar = section.getString("boss-bar", "%-4s | Bodies: %-4s | Vehicles: %-4s");
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
        public final boolean enable;
        public final Precision precision;
        public final int workerThreads;
        public final SimulationSettings SIMULATION;

        public PhysicsSettings(ConfigurationSection section, FileConfiguration root) {
            this.SIMULATION = new SimulationSettings(section.getConfigurationSection("simulation"), root);

            this.enable = section.getBoolean("enable", false);

            // Parse Precision from String "DP" or "SP"
            String precStr = section.getString("precision", "SP");
            this.precision = "DP".equalsIgnoreCase(precStr) ? Precision.DP : Precision.SP;

            this.workerThreads = section.getInt("worker-threads", 2);
        }

        public static class SimulationSettings {
            public final int workerThreads;
            public final float rayonInflate;

            public SimulationSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.workerThreads = 1;
                    this.rayonInflate = 3.0f;
                    return;
                }

                this.workerThreads = section.getInt("worker-threads", 1);
                this.rayonInflate = (float) section.getDouble("rayon.inflate", 3.0D);
            }
        }
    }
}
