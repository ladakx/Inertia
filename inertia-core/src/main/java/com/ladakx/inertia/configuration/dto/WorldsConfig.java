package com.ladakx.inertia.configuration.dto;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.serializers.RVec3Serializer;
import com.ladakx.inertia.common.serializers.Vec3Serializer;
import com.ladakx.inertia.physics.world.terrain.SimulationType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldsConfig {
    private final Map<String, WorldProfile> worlds = new HashMap<>();

    public WorldsConfig(FileConfiguration cfg) {
        Set<String> keys = cfg.getKeys(false);
        if (keys.isEmpty()) {
            InertiaLogger.warn("worlds.yml (or config section) is empty! No physics worlds loaded.");
            return;
        }
        for (String key : keys) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section != null) {
                try {
                    WorldProfile profile = parseWorld(section);
                    worlds.put(key, profile);
                } catch (Exception e) {
                    InertiaLogger.error("Failed to parse world settings for '" + key + "': " + e.getMessage());
                }
            }
        }
        InertiaLogger.info("Loaded physics settings for " + worlds.size() + " worlds.");
    }

    private WorldProfile parseWorld(ConfigurationSection section) {
        Vec3 gravity = Vec3Serializer.serialize(section.getString("gravity", "0.0 -17.18 0.0"));
        int tickRate = Math.max(1, section.getInt("tick-rate", 20));
        int collisionSteps = section.getInt("collision-steps", 4);

        // Chunk Management
        ConfigurationSection chunkSec = section.getConfigurationSection("chunk-management");
        ChunkManagementSettings chunkSettings;
        if (chunkSec != null) {
            chunkSettings = new ChunkManagementSettings(
                    chunkSec.getBoolean("generate-on-load", true),
                    chunkSec.getBoolean("remove-on-unload", true),
                    chunkSec.getBoolean("update-on-block-change", true),
                    chunkSec.getInt("update-debounce-ticks", 5),
                    chunkSec.getInt("mesh-apply-per-tick", 4)
            );
        } else {
            chunkSettings = new ChunkManagementSettings(true, true, true, 5, 4);
        }

        ConfigurationSection perfSec = section.getConfigurationSection("performance");
        int maxBodiesDefault = section.getInt("max-bodies", 65536);
        int maxBodies = perfSec != null ? perfSec.getInt("max-bodies", maxBodiesDefault) : maxBodiesDefault;
        int numBodyMutexes = perfSec != null ? perfSec.getInt("num-body-mutexes", 0) : 0;
        int maxBodyPairs = perfSec != null ? perfSec.getInt("max-body-pairs", 65536) : 65536;
        int maxContactConstraints = perfSec != null ? perfSec.getInt("max-contact-constraints", 10240) : 10240;
        int tempAllocSize = perfSec != null ? perfSec.getInt("temp-allocator-size", 10 * 1024 * 1024) : 10 * 1024 * 1024;
        PerformanceSettings perfSettings = new PerformanceSettings(maxBodies, numBodyMutexes, maxBodyPairs, maxContactConstraints, tempAllocSize);

        ConfigurationSection solvSec = section.getConfigurationSection("solver");
        int velSteps = solvSec != null ? solvSec.getInt("velocity-iterations", 10) : 10;
        int posSteps = solvSec != null ? solvSec.getInt("position-iterations", 2) : 2;
        float baumgarte = solvSec != null ? (float) solvSec.getDouble("baumgarte-stabilization", 0.2) : 0.2f;
        float specContactDist = solvSec != null ? (float) solvSec.getDouble("speculative-contact-distance", 0.02) : 0.02f;
        float penSlop = solvSec != null ? (float) solvSec.getDouble("penetration-slop", 0.02) : 0.02f;
        float linCastThresh = solvSec != null ? (float) solvSec.getDouble("linear-cast-threshold", 0.75) : 0.75f;
        float linCastMaxPen = solvSec != null ? (float) solvSec.getDouble("linear-cast-max-penetration", 0.25) : 0.25f;
        float manifoldTol = solvSec != null ? (float) solvSec.getDouble("manifold-tolerance", 1.0e-3) : 1.0e-3f;
        float maxPenDist = solvSec != null ? (float) solvSec.getDouble("max-penetration-distance", 0.2) : 0.2f;
        boolean warmStart = solvSec == null || solvSec.getBoolean("constraint-warm-start", true);
        boolean useCache = solvSec == null || solvSec.getBoolean("use-body-pair-contact-cache", true);
        boolean splitIslands = solvSec == null || solvSec.getBoolean("use-large-island-splitter", true);
        boolean allowSleep = solvSec == null || solvSec.getBoolean("allow-sleeping", true);
        boolean deterministic = solvSec == null || solvSec.getBoolean("deterministic-simulation", true);

        SolverSettings solverSettings = new SolverSettings(
                velSteps, posSteps, baumgarte,
                specContactDist, penSlop, linCastThresh, linCastMaxPen,
                manifoldTol, maxPenDist,
                warmStart, useCache, splitIslands, allowSleep, deterministic
        );

        ConfigurationSection sleepSec = section.getConfigurationSection("sleeping");
        float sleepThreshold = sleepSec != null ? (float) sleepSec.getDouble("point-velocity-threshold", 0.03) : 0.03f;
        float timeBeforeSleep = sleepSec != null ? (float) sleepSec.getDouble("time-before-sleep", 0.5) : 0.5f;
        SleepSettings sleepSettings = new SleepSettings(sleepThreshold, timeBeforeSleep);

        ConfigurationSection simSec = section.getConfigurationSection("simulation");
        boolean simEnable = false;
        SimulationType simType = SimulationType.NONE;
        FloorPlaneSettings floorSettings = null;
        GreedyMeshingSettings greedySettings = null;

        if (simSec != null) {
            simEnable = simSec.getBoolean("enable", false);
            try {
                simType = SimulationType.valueOf(simSec.getString("type", "NONE").toUpperCase());
            } catch (IllegalArgumentException e) {
                InertiaLogger.warn("Unknown simulation type: " + simSec.getString("type"));
            }

            if (simSec.contains("floor-plane")) {
                ConfigurationSection floorSec = simSec.getConfigurationSection("floor-plane");
                if (floorSec != null) {
                    float yLevel = (float) floorSec.getDouble("y-level", 0.0);
                    float ySize = (float) floorSec.getDouble("y-size", 1.0);
                    float friction = (float) floorSec.getDouble("friction", 1.0);
                    float restitution = (float) floorSec.getDouble("restitution", 0.0);

                    ConfigurationSection fSizeSec = floorSec.getConfigurationSection("size");
                    Vec3 fOrigin = new Vec3(0,0,0);
                    float minX = -5000000f, minZ = -5000000f;
                    float maxX = 5000000f, maxZ = 5000000f;

                    if (fSizeSec != null) {
                        fOrigin = Vec3Serializer.serializeXZ(fSizeSec.getString("origin", "0 0"));
                        String minStr = fSizeSec.getString("min");
                        if (minStr != null) {
                            String[] parts = minStr.split("\\s+");
                            if (parts.length >= 2) {
                                minX = Float.parseFloat(parts[0]);
                                minZ = Float.parseFloat(parts[1]);
                            }
                        }
                        String maxStr = fSizeSec.getString("max");
                        if (maxStr != null) {
                            String[] parts = maxStr.split("\\s+");
                            if (parts.length >= 2) {
                                maxX = Float.parseFloat(parts[0]);
                                maxZ = Float.parseFloat(parts[1]);
                            }
                        }
                    }
                    floorSettings = new FloorPlaneSettings(yLevel, ySize, friction, restitution,
                            new FloorBounds(fOrigin, minX, minZ, maxX, maxZ));
                }
            }

            if (simSec.contains("greedy-meshing")) {
                ConfigurationSection gmSec = simSec.getConfigurationSection("greedy-meshing");
                if (gmSec != null) {
                    GreedyMeshShapeType shapeType = parseGreedyMeshShapeType(gmSec.getString("shape-type", "MeshShape"));
                    greedySettings = new GreedyMeshingSettings(
                            gmSec.getBoolean("vertical-merging", true),
                            gmSec.getInt("max-vertical-size", 64),
                            shapeType,
                            gmSec.getBoolean("fast-chunk-capture", true)
                    );
                }
            } else if (simSec.contains("greedy-mesh")) {
                ConfigurationSection gmSec = simSec.getConfigurationSection("greedy-mesh");
                if (gmSec != null) {
                    GreedyMeshShapeType shapeType = parseGreedyMeshShapeType(gmSec.getString("shape-type", "MeshShape"));
                    greedySettings = new GreedyMeshingSettings(
                            gmSec.getBoolean("vertical-merging", true),
                            gmSec.getInt("max-vertical-size", 64),
                            shapeType,
                            gmSec.getBoolean("fast-chunk-capture", true)
                    );
                }
            }
        }

        if (floorSettings == null) {
            floorSettings = new FloorPlaneSettings(0, 1, 1, 0, new FloorBounds(new Vec3(0,0,0), -100, -100, 100, 100));
        }
        if (greedySettings == null) {
            greedySettings = new GreedyMeshingSettings(true, 64, GreedyMeshShapeType.MESH_SHAPE, true);
        }

        SimulationSettings simulation = new SimulationSettings(simEnable, simType, floorSettings, greedySettings);

        ConfigurationSection sizeSec = section.getConfigurationSection("size");
        WorldSizeSettings sizeSettings = parseWorldSize(sizeSec);

        return new WorldProfile(gravity, tickRate, collisionSteps, solverSettings, perfSettings, sleepSettings, simulation, sizeSettings, chunkSettings);
    }

    private WorldSizeSettings parseWorldSize(ConfigurationSection sizeSec) {
        boolean createWalls = false;
        boolean killBelowMinY = false;
        boolean preventExit = true;
        Vec3 heightSize;
        RVec3 finalOrigin;
        RVec3 finalMin;
        RVec3 finalMax;

        if (sizeSec == null) {
            finalOrigin = new RVec3(0, 0, 0);
            heightSize = new Vec3(-128, 0, 1024);
            finalMin = new RVec3(-1000, -128, -1000);
            finalMax = new RVec3(1000, 1024, 1000);
        } else {
            createWalls = sizeSec.getBoolean("create-walls", false);
            killBelowMinY = sizeSec.getBoolean("kill-below-min-y", false);
            preventExit = sizeSec.getBoolean("prevent-exit", true);

            RVec3 configOrigin = null;
            if (sizeSec.contains("origin")) {
                configOrigin = parseVectorXZ(sizeSec.getString("origin"), 0.0);
            }

            RVec3 configMin = null;
            if (sizeSec.contains("min")) {
                configMin = RVec3Serializer.serializeXZ(sizeSec.getString("min"));
            }

            RVec3 configMax = null;
            if (sizeSec.contains("max")) {
                configMax = RVec3Serializer.serializeXZ(sizeSec.getString("max"));
            }

            heightSize = new Vec3(-128, 0, 1024);
            if (sizeSec.contains("height-size")) {
                heightSize = Vec3Serializer.serializeXZ(sizeSec.getString("height-size"));
            }

            if (configMin != null && configMax != null) {
                finalMin = configMin;
                finalMax = configMax;
                if (configOrigin != null) {
                    finalOrigin = configOrigin;
                } else {
                    finalOrigin = new RVec3(
                            (finalMin.xx() + finalMax.xx()) * 0.5,
                            0.0,
                            (finalMin.zz() + finalMax.zz()) * 0.5
                    );
                }
            } else {
                RVec3 center = (configOrigin != null) ? configOrigin : new RVec3(0, 0, 0);
                double dx = 500.0;
                double dz = 500.0;
                if (sizeSec.contains("radius")) {
                    double radius = sizeSec.getDouble("radius");
                    dx = radius;
                    dz = radius;
                } else {
                    if (sizeSec.contains("width")) dx = sizeSec.getDouble("width") / 2.0;
                    if (sizeSec.contains("length")) dz = sizeSec.getDouble("length") / 2.0;
                }

                finalMin = new RVec3(center.xx() - dx, configMin != null ? configMin.y() : 0, center.zz() - dz);
                finalMax = new RVec3(center.xx() + dx, configMax != null ? configMax.y() : 0, center.zz() + dz);
                finalOrigin = center;
            }
        }

        float lMinX = (float) (finalMin.xx() - finalOrigin.xx());
        float lMinY = heightSize.getX();
        float lMinZ = (float) (finalMin.zz() - finalOrigin.zz());
        float lMaxX = (float) (finalMax.xx() - finalOrigin.xx());
        float lMaxY = heightSize.getZ();
        float lMaxZ = (float) (finalMax.zz() - finalOrigin.zz());

        Vec3 localMin = new Vec3(lMinX, lMinY, lMinZ);
        Vec3 localMax = new Vec3(lMaxX, lMaxY, lMaxZ);

        return new WorldSizeSettings(
                finalOrigin, finalMin, finalMax, heightSize, localMin, localMax,
                createWalls, killBelowMinY, preventExit
        );
    }

    private RVec3 parseVectorXZ(String str, double fixedY) {
        if (str == null) return null;
        String[] parts = str.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                double x = Double.parseDouble(parts[0]);
                double z = Double.parseDouble(parts[1]);
                return new RVec3(x, fixedY, z);
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

    private GreedyMeshShapeType parseGreedyMeshShapeType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return GreedyMeshShapeType.MESH_SHAPE;
        }

        String normalized = rawValue.trim().replace("_", "").replace("-", "").toUpperCase();
        return switch (normalized) {
            case "MESHSHAPE" -> GreedyMeshShapeType.MESH_SHAPE;
            case "COMPOUNDSHAPE" -> GreedyMeshShapeType.COMPOUND_SHAPE;
            default -> {
                InertiaLogger.warn("Unknown greedy-meshing shape-type: " + rawValue + ". Fallback to MeshShape.");
                yield GreedyMeshShapeType.MESH_SHAPE;
            }
        };
    }

    public WorldProfile getWorldSettings(String worldName) {
        return worlds.get(worldName);
    }

    public Map<String, WorldProfile> getAllWorlds() {
        return worlds;
    }

    public record WorldProfile(
            Vec3 gravity,
            int tickRate,
            int collisionSteps,
            SolverSettings solver,
            PerformanceSettings performance,
            SleepSettings sleeping,
            SimulationSettings simulation,
            WorldSizeSettings size,
            ChunkManagementSettings chunkManagement
    ) {}

    public record SolverSettings(
            int velocityIterations,
            int positionIterations,
            float baumgarte,
            float speculativeContactDistance,
            float penetrationSlop,
            float linearCastThreshold,
            float linearCastMaxPenetration,
            float manifoldTolerance,
            float maxPenetrationDistance,
            boolean constraintWarmStart,
            boolean useBodyPairContactCache,
            boolean useLargeIslandSplitter,
            boolean allowSleeping,
            boolean deterministicSimulation
    ) {}

    public record PerformanceSettings(int maxBodies, int numBodyMutexes, int maxBodyPairs, int maxContactConstraints, int tempAllocatorSize) {}

    public record SleepSettings(float pointVelocityThreshold, float timeBeforeSleep) {}

    public record SimulationSettings(boolean enabled, SimulationType type, FloorPlaneSettings floorPlane, GreedyMeshingSettings greedyMeshing) {}

    public record FloorPlaneSettings(float yLevel, float ySize, float friction, float restitution, FloorBounds bounds) {}

    public record GreedyMeshingSettings(boolean verticalMerging,
                                        int maxVerticalSize,
                                        GreedyMeshShapeType shapeType,
                                        boolean fastChunkCapture) {}

    public enum GreedyMeshShapeType {
        MESH_SHAPE,
        COMPOUND_SHAPE
    }

    public record FloorBounds(Vec3 origin, float minX, float minZ, float maxX, float maxZ) {}

    public record WorldSizeSettings(
            RVec3 origin,
            RVec3 worldMin,
            RVec3 worldMax,
            Vec3 heightSize,
            Vec3 localMin,
            Vec3 localMax,
            boolean createWalls,
            boolean killBelowMinY,
            boolean preventExit
    ) {}

    public record ChunkManagementSettings(
            boolean generateOnLoad,
            boolean removeOnUnload,
            boolean updateOnBlockChange,
            int updateDebounceTicks,
            int maxMeshAppliesPerTick
    ) {}
}
