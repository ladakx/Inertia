package com.ladakx.inertia.files.config;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.nativelib.Precision;
import com.ladakx.inertia.utils.serializers.BoundingSerializer;
import com.ladakx.inertia.utils.serializers.Vec3Serializer;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Immutable Data Object representing the config.yml file.
 * Updated to match the new YAML structure (kebab-case) and include default fallback logic.
 */
public class InertiaConfig {

    public final GeneralSettings GENERAL;
    public final SimulationSettings SIMULATION;
    public final PhysicsSettings PHYSICS;

    public InertiaConfig(FileConfiguration cfg) {
        this.GENERAL = new GeneralSettings(cfg.getConfigurationSection("general"), cfg);
        this.SIMULATION = new SimulationSettings(cfg.getConfigurationSection("simulation"), cfg);
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
            public final boolean hitboxEnableLines;
            public final float hitboxParticleSize;
            public final int hitboxParticleCount;
            public final Map<String, DebugBlock> debugBlocks = new HashMap<>();
            public final int blockDebugMaxRadius;
            public final String debugPlaceholderBar;

            public DebugSettings(ConfigurationSection section, FileConfiguration root) {
                if (section == null) {
                    this.hitboxEnableLines = false;
                    this.hitboxParticleSize = 0.25f;
                    this.hitboxParticleCount = 16;
                    this.blockDebugMaxRadius = 16;
                    this.debugPlaceholderBar = "Debug info...";
                    return;
                }

                this.hitboxEnableLines = section.getBoolean("hitbox.lines", false);
                this.hitboxParticleSize = (float) section.getDouble("hitbox.size", 0.25D);
                this.hitboxParticleCount = section.getInt("hitbox.count", 16);
                this.blockDebugMaxRadius = section.getInt("block.max-radius", 16);
                this.debugPlaceholderBar = section.getString("boss-bar", "%-4s | Bodies: %-4s | Vehicles: %-4s");

                ConfigurationSection blocksSection = section.getConfigurationSection("block.blocks");
                if (blocksSection != null) {
                    for (String key : blocksSection.getKeys(false)) {
                        String fullPath = blocksSection.getCurrentPath() + "." + key;
                        this.debugBlocks.put(key, new DebugBlock(fullPath, root));
                    }
                }
            }

            public static class DebugBlock {
                public final float mass;
                public final float angularDamping;
                public final float linearDamping;
                public final float restitution;
                public final float friction;
                public final List<BoundingBox> box;
                public final BlockData activeBlockData;
                public final BlockData sleepBlockData;

                public DebugBlock(String path, FileConfiguration cfg) {
                    Material activeMaterial = Material.valueOf(cfg.getString(path + ".material-active", "EMERALD_BLOCK").toUpperCase(Locale.ROOT));
                    Material sleepMaterial = Material.valueOf(cfg.getString(path + ".material-sleep", cfg.getString(path + ".material-active", "EMERALD_BLOCK")).toUpperCase(Locale.ROOT));

                    this.activeBlockData = activeMaterial.createBlockData();
                    this.sleepBlockData = sleepMaterial.createBlockData();
                    this.box = BoundingSerializer.parseListFromStrings(cfg.getStringList(path + ".shape"));
                    this.mass = (float) cfg.getDouble(path + ".mass", 75.0D);
                    this.angularDamping = (float) cfg.getDouble(path + ".angular-damping", 0.1D);
                    this.linearDamping = (float) cfg.getDouble(path + ".linear-damping", 0.3D);
                    this.restitution = (float) cfg.getDouble(path + ".restitution", 0.025D);
                    this.friction = (float) cfg.getDouble(path + ".friction", 1.0D);
                }
            }
        }
    }

    // ==========================================
    // Simulation Settings
    // ==========================================
    public static class SimulationSettings {
        public final boolean enable;
        public final String type;
        public final int threads;
        public final float rayonInflate;

        public SimulationSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.enable = false;
                this.type = "RAYON";
                this.threads = 1;
                this.rayonInflate = 3.0f;
                return;
            }
            this.enable = section.getBoolean("enable", false);
            this.type = section.getString("type", "RAYON");
            this.threads = section.getInt("threads", 1);
            this.rayonInflate = (float) section.getDouble("settings.rayon.inflate", 3.0D);
        }
    }

    // ==========================================
    // Physics Settings
    // ==========================================
    public static class PhysicsSettings {
        public final boolean enable;
        public final Precision precision;
        public final int workerThreads;
        public final Set<WorldSettings> worlds = new HashSet<>();

        // Створюємо статичний дефолтний світ на випадок, якщо нічого не знайдено
        private static final WorldSettings DEFAULT_WORLD = new WorldSettings();

        public PhysicsSettings(ConfigurationSection section, FileConfiguration root) {
            if (section == null) {
                this.enable = false;
                this.precision = Precision.SP;
                this.workerThreads = 1;
                return;
            }
            this.enable = section.getBoolean("enable", false);

            // Parse Precision from String "DP" or "SP"
            String precStr = section.getString("precision", "SP");
            this.precision = "DP".equalsIgnoreCase(precStr) ? Precision.DP : Precision.SP;

            this.workerThreads = section.getInt("worker-threads", 2);

            ConfigurationSection worldsSection = section.getConfigurationSection("worlds");
            if (worldsSection != null) {
                for (String worldName : worldsSection.getKeys(false)) {
                    worlds.add(new WorldSettings(worldName, worldsSection.getCurrentPath() + "." + worldName, root));
                }
            }
        }

        /**
         * Отримує налаштування світу за назвою.
         * Якщо світ не знайдено - повертає дефолтний світ замість null.
         */
        public WorldSettings getWorld(String name) {
            for (WorldSettings world : worlds) {
                if (world.name.equalsIgnoreCase(name)) return world;
            }

            return DEFAULT_WORLD;
        }

        public static class WorldSettings {
            public final String name;
            public final Vector3f gravity;

            // Plane Shape Settings
            public final boolean floorPlaneEnable;
            public final float floorPlaneY;
            public final int tickRate;
            public final int maxBodies;
            public final int collisionSteps;

            public final Vector3f maxPoint;
            public final Vector3f minPoint;
            public final Vector2f center;

            /**
             * Приватний конструктор для створення "Дефолтного" світу (Hardcoded values).
             * Використовується для PhysicsSettings.DEFAULT_WORLD.
             */
            private WorldSettings() {
                this.name = "default_fallback";
                this.gravity = new Vector3f(0, -17.18f, 0); // Стандартна гравітація Землі
                this.floorPlaneEnable = true;
                this.floorPlaneY = 0.0f;
                this.tickRate = 20;
                this.maxBodies = 5000;
                this.collisionSteps = 2;
                this.maxPoint = new Vector3f(200000, 1024, 200000); // Великі безпечні межі
                this.minPoint = new Vector3f(-200000, 0, -200000);
                this.center = new Vector2f(0, 0);
            }

            /**
             * Основний конструктор для завантаження з конфігу.
             */
            public WorldSettings(String name, String path, FileConfiguration cfg) {
                this.name = name;

                // Гравітація (дефолт якщо null)
                Vector3f loadedGravity = Vec3Serializer.serialize(path + ".gravity", cfg);
                this.gravity = loadedGravity != null ? loadedGravity : new Vector3f(0, -9.81f, 0);

                // Plane shape
                this.floorPlaneEnable = cfg.getBoolean(path + ".floor-plane.enable", true);
                this.floorPlaneY = (float) cfg.getDouble(path + ".floor-plane.y-level", 0.0);
                this.tickRate = cfg.getInt(path + ".tick-rate", 20);
                this.maxBodies = cfg.getInt(path + ".max-bodies", 5000);
                this.collisionSteps = cfg.getInt(path + ".collision-steps", 2);

                // Розміри (Size) - перевірка на null і встановлення дефолтних значень
                Vector3f loadedMax = Vec3Serializer.serialize(path + ".size.max", cfg);
                this.maxPoint = loadedMax != null ? loadedMax : new Vector3f(200000, 1024, 200000);

                Vector3f loadedMin = Vec3Serializer.serialize(path + ".size.min", cfg);
                this.minPoint = loadedMin != null ? loadedMin : new Vector3f(-200000, -64, -200000);

                // maxPoint і minPoint гарантовано не null, можна рахувати центр
                this.center = new Vector2f(
                        (maxPoint.x + minPoint.x) / 2.0F,
                        (maxPoint.z + minPoint.z) / 2.0F
                );
            }
        }
    }
}