package com.ladakx.inertia.files.config;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.SolverType;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.utils.serializers.BoundingSerializer;
import com.ladakx.inertia.utils.serializers.Vector3fSerializer;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class PluginCFG {

    public final General GENERAL;
    public final Events EVENTS;
    public final Simulation SIMULATION;
    public final Physics PHYSICS;

    public PluginCFG(FileConfiguration cfg) {
        this.GENERAL = new General("General", cfg);
        this.EVENTS = new Events("Events", cfg);
        this.SIMULATION = new Simulation("Simulation", cfg);
        this.PHYSICS = new Physics("Physics", cfg);
    }

    public class General {
        public final String lang;
        public final Debug DEBUG;

        public General(String path, FileConfiguration cfg) {
            this.lang = cfg.getString(path+".Lang", "en");
            this.DEBUG = new Debug(path+".Debug", cfg);
        }

        public class Debug {
            // hitbox
            public final boolean hitboxEnableLines;
            public final float hitboxParticleSize;
            public final int hitboxParticleCount;

            // block debug
            public final HashMap<String, DebugBlock> debugBlocks = new HashMap<>();
            public final int blockDebugMaxRadius;

            // debug bar placeholder
            public final String debugPlaceholderBar;

            public Debug(String path, FileConfiguration cfg) {
                // hitbox
                this.hitboxEnableLines = cfg.getBoolean(path+".Hitbox.Lines", false);
                this.hitboxParticleSize = (float) cfg.getDouble(path+".Hitbox.Size", 0.25D);
                this.hitboxParticleCount = cfg.getInt(path+".Hitbox.Count", 16);

                // block
                ConfigurationSection section = cfg.getConfigurationSection(path+".Block.Blocks");
                if (section != null) {
                    for (String block : section.getKeys(false)) {
                        this.debugBlocks.put(block, new DebugBlock(path+".Block.Blocks."+block, cfg));
                    }
                }
                this.blockDebugMaxRadius = cfg.getInt(path+".Block.Max_Radius", 16);

                // debug bar
                this.debugPlaceholderBar = cfg.getString(path+".Debug_Bar", "%-4s | Bodies: %-4s | Vehicles: %-4s | Physics MSPT: %-4s | Simulation MSPT: %-4s");
            }

            public class DebugBlock {
                public final float mass;
                public final float angularDamping;
                public final float linearDamping;
                public final float restitution;
                public final float friction;

                public final List<BoundingBox> box;

                public final BlockData activeBlockData;
                public final BlockData sleepBlockData;

                public DebugBlock(String path, FileConfiguration cfg) {
                    Material activeMaterial = Material.valueOf(cfg.getString(path+".Material_Active", "EMERALD_BLOCK").toUpperCase(Locale.ROOT));
                    Material sleepMaterial = Material.valueOf(cfg.getString(path+".Material_Sleep", cfg.getString(path+".Material_Active", "EMERALD_BLOCK")).toUpperCase(Locale.ROOT));

                    this.activeBlockData = activeMaterial.createBlockData();
                    this.sleepBlockData = sleepMaterial.createBlockData();

                    this.box = BoundingSerializer.parseListFromStrings(cfg.getStringList(path+".Shape"));

                    this.mass = (float) cfg.getDouble(path+".Mass", 75.0D);
                    this.angularDamping = (float) cfg.getDouble(path+".Angular_Damping", 0.1D);
                    this.linearDamping = (float) cfg.getDouble(path+".Linear_Damping", 0.3D);
                    this.restitution = (float) cfg.getDouble(path+".Restitution", 0.025D);
                    this.friction = (float) cfg.getDouble(path+".Friction", 1.0D);
                }
            }
        }
    }

    public class Events {
        public final boolean collisionEnable;
        public final Vector3f collisionThreshold;

        public Events(String path, FileConfiguration cfg) {
            this.collisionEnable = cfg.getBoolean(path + ".Collision.Enable", false);
            this.collisionThreshold = Vector3fSerializer.serialize(path+".Collision.Threshold", cfg);
        }
    }

    public class Physics {
        public final boolean enable;
        public final int threads;
        public final int tickRate;

        public final Worlds WORLDS;

        public Physics(String path, FileConfiguration cfg) {
            this.enable = cfg.getBoolean(path + ".Enable", false);
            this.tickRate = cfg.getInt(path + ".Performance.Tick_Rate", 20);
            this.threads = cfg.getInt(path + ".Performance.Threads", 1);

            this.WORLDS = new Worlds(path + ".Worlds", cfg);
        }

        public class Worlds {
            public final Set<World> worlds;

            public Worlds(String path, FileConfiguration cfg) {
                this.worlds = new HashSet<>();

                ConfigurationSection section = cfg.getConfigurationSection(path);
                if (section == null) return;

                for (String world : section.getKeys(false)) {
                    this.worlds.add(new World(world, path + "." + world, cfg));
                }
            }

            public World getWorld(String name) {
                for (World world : worlds) {
                    if (world.name.equalsIgnoreCase(name)) {
                        return world;
                    }
                }

                return null;
            }

            public class World {
                public final String name;

                public final Vector3f gravity;
                public final float accuracy;

                public final SolverType solverType;
                public final int iterations;

                public final float maxTimeStep;
                public final int maxSubSteps;

                public final Vector3f maxPoint;
                public final Vector3f minPoint;
                public final Vector2f center;

                public World(String name, String path, FileConfiguration cfg) {
                    this.name = name;
                    this.gravity = Vector3fSerializer.serialize(path+".Gravity", cfg);
                    this.accuracy = (float) cfg.getDouble(path + ".Accuracy", 0.166D);

                    this.solverType = SolverType.valueOf(cfg.getString(path + ".Solver.Type", "SI"));
                    this.iterations = cfg.getInt(path + ".Solver.Iterations", 10);

                    this.maxTimeStep = (float) cfg.getDouble(path + ".Step.Max_Time", 0.1D);
                    this.maxSubSteps = cfg.getInt(path + ".Step.Max_Sub_Steps", 4);

                    this.maxPoint = Vector3fSerializer.serialize(path+".Size.Max", cfg);
                    this.minPoint = Vector3fSerializer.serialize(path+".Size.Min", cfg);
                    this.center = new Vector2f(
                            (maxPoint.x + minPoint.x) / 2.0F,
                            (maxPoint.z + minPoint.z) / 2.0F
                    );
                }
            }
        }
    }

    public class Simulation {
        public final boolean enable;
        public final int threads;

        public final Settings SETTINGS;

        public Simulation(String path, FileConfiguration cfg) {
            this.enable = cfg.getBoolean(path + ".Enable", false);

            this.threads = cfg.getInt(path + ".Threads", 1);
            this.SETTINGS = new Settings(path + ".Settings", cfg);
        }

        public class Settings {
            public final Rayon RAYON;

            public Settings(String path, FileConfiguration cfg) {
                this.RAYON = new Rayon(path + ".Rayon", cfg);
            }

            public class Rayon {
                public final float inflate;

                public Rayon(String path, FileConfiguration cfg) {
                    this.inflate = (float) cfg.getDouble(path+".Inflate", 3.0D);
                }
            }
        }
    }
}
