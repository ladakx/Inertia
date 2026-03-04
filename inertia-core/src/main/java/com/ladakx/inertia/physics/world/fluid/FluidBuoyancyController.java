package com.ladakx.inertia.physics.world.fluid;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import com.ladakx.inertia.configuration.dto.InertiaConfig;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FluidBuoyancyController {
    private static final int MAX_SURFACE_SEARCH_UP = 16;
    private static final float INVALID_SURFACE = -1f;

    private static final float COVERAGE_BASE = 0.25f;
    private static final float COVERAGE_SCALE = 0.75f;
    private static final float FLOW_STRENGTH = 0.35f;

    private final PhysicsWorld world;
    private final AtomicReference<FluidContactSnapshot> published = new AtomicReference<>(FluidContactSnapshot.EMPTY);

    private final float buoyancyScale;
    private final float waterDensity;
    private final float lavaDensity;
    private final float waterLinearDrag;
    private final float waterAngularDrag;
    private final float lavaLinearDrag;
    private final float lavaAngularDrag;
    private final float minCoverage;
    private final float minHalfExtent;

    public FluidBuoyancyController(@NotNull PhysicsWorld world,
                                   @NotNull InertiaConfig.PhysicsSettings.FluidsSettings settings) {
        this.world = Objects.requireNonNull(world, "world");
        Objects.requireNonNull(settings, "settings");

        this.buoyancyScale = settings.buoyancyScale;
        this.waterDensity = settings.waterDensity;
        this.lavaDensity = settings.lavaDensity;
        this.waterLinearDrag = settings.waterLinearDrag;
        this.waterAngularDrag = settings.waterAngularDrag;
        this.lavaLinearDrag = settings.lavaLinearDrag;
        this.lavaAngularDrag = settings.lavaAngularDrag;
        this.minCoverage = settings.minSubmersionToSimulate;
        this.minHalfExtent = settings.minShapeHalfExtent;
    }

    public void refreshContacts(@NotNull Collection<AbstractPhysicsBody> bodies) {
        Objects.requireNonNull(bodies, "bodies");

        World bukkitWorld = world.getWorldBukkit();
        RVec3 origin = world.getOrigin();
        SnapshotBuilder builder = new SnapshotBuilder(Math.min(256, bodies.size()));

        for (AbstractPhysicsBody object : bodies) {
            if (object == null || !object.isValid()) {
                continue;
            }

            Body body = object.getBody();
            if (body == null || body.getMotionType() != EMotionType.Dynamic) {
                continue;
            }

            WorldAabb aabb = toWorldAabb(body, origin);
            FluidProbe probe = probeFluid(bukkitWorld, aabb);
            if (probe == null || probe.coverage() < minCoverage) {
                continue;
            }

            builder.add(body.getId(), probe);
        }

        published.set(builder.build());
    }

    public void applyForces(float dtSeconds) {
        if (dtSeconds <= 0f) {
            return;
        }

        FluidContactSnapshot snapshot = published.get();
        if (snapshot.size() == 0) {
            return;
        }

        PhysicsSystem system = world.getPhysicsSystem();
        BodyInterface bodyInterface = system.getBodyInterfaceNoLock();
        var lockInterface = system.getBodyLockInterfaceNoLock();

        RVec3 origin = world.getOrigin();
        Vec3 gravity = system.getGravity();
        ForceScratch scratch = new ForceScratch();

        for (int i = 0; i < snapshot.size(); i++) {
            int bodyId = snapshot.bodyIds()[i];
            if (!bodyInterface.isAdded(bodyId)) {
                continue;
            }

            float coverage = clamp01(snapshot.areaFractions()[i]);
            if (coverage <= 0f) {
                continue;
            }

            FluidMedium medium = FluidMedium.fromId(snapshot.mediumIds()[i]);
            ForceProfile profile = forceProfile(medium, coverage);

            scratch.surfacePoint.set(
                    snapshot.centerXs()[i] - origin.xx(),
                    snapshot.surfaceHeights()[i] - origin.yy(),
                    snapshot.centerZs()[i] - origin.zz()
            );
            scratch.flowVector.set(snapshot.flowXs()[i], snapshot.flowYs()[i], snapshot.flowZs()[i]);

            applyBodyImpulse(lockInterface, bodyId, scratch, profile, gravity, dtSeconds);
        }
    }

    private void applyBodyImpulse(ConstBodyLockInterface lockInterface,
                                  int bodyId,
                                  ForceScratch scratch,
                                  ForceProfile profile,
                                  Vec3 gravity,
                                  float dtSeconds) {
        try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
            if (!lock.succeeded()) {
                return;
            }
            Body body = lock.getBody();
            if (body == null || !body.isActive() || body.getMotionType() != EMotionType.Dynamic) {
                return;
            }

            body.applyBuoyancyImpulse(
                    scratch.surfacePoint,
                    scratch.surfaceNormal,
                    profile.buoyancy(),
                    profile.linearDrag(),
                    profile.angularDrag(),
                    scratch.flowVector,
                    gravity,
                    dtSeconds
            );
        } catch (Throwable ignored) {
            // Body can disappear between broad and narrow phases.
        }
    }

    private WorldAabb toWorldAabb(Body body, RVec3 origin) {
        RVec3 localPos = body.getPosition();
        double cx = localPos.xx() + origin.xx();
        double cy = localPos.yy() + origin.yy();
        double cz = localPos.zz() + origin.zz();

        ConstAaBox bounds = body.getShape().getLocalBounds();
        Vec3 min = bounds.getMin();
        Vec3 max = bounds.getMax();

        double hx = Math.max(minHalfExtent, (max.getX() - min.getX()) * 0.5d);
        double hy = Math.max(minHalfExtent, (max.getY() - min.getY()) * 0.5d);
        double hz = Math.max(minHalfExtent, (max.getZ() - min.getZ()) * 0.5d);

        return new WorldAabb(cx - hx, cx + hx, cy - hy, cy + hy, cz - hz, cz + hz, (float) (cy - hy));
    }

    private FluidProbe probeFluid(World world, WorldAabb aabb) {
        GridRange grid = toGridRange(world, aabb);
        if (!grid.valid()) {
            return null;
        }

        int totalColumns = 0;
        int wetColumns = 0;
        int waterHits = 0;
        int lavaHits = 0;
        float surfaceSum = 0f;
        float centerXSum = 0f;
        float centerZSum = 0f;

        for (int x = grid.minX(); x <= grid.maxX(); x++) {
            for (int z = grid.minZ(); z <= grid.maxZ(); z++) {
                totalColumns++;

                ColumnFluid hit = findColumnFluid(world, x, z, grid.minY(), grid.maxY(), aabb.bodyBottomY());
                if (hit == null) {
                    continue;
                }

                wetColumns++;
                if (hit.mediumId == FluidMedium.LAVA.id) {
                    lavaHits++;
                } else {
                    waterHits++;
                }

                surfaceSum += hit.surfaceY;
                centerXSum += x + 0.5f;
                centerZSum += z + 0.5f;
            }
        }

        if (wetColumns == 0 || totalColumns == 0) {
            return null;
        }

        int mediumId = lavaHits > waterHits ? FluidMedium.LAVA.id : FluidMedium.WATER.id;
        float surfaceY = surfaceSum / wetColumns;
        float coverage = (float) wetColumns / (float) totalColumns;
        float centerX = centerXSum / wetColumns;
        float centerZ = centerZSum / wetColumns;

        Vec3 flow = estimateFluidFlow(world, centerX, surfaceY, centerZ, mediumId);
        return new FluidProbe(mediumId, surfaceY, coverage, centerX, centerZ, flow.getX(), flow.getY(), flow.getZ());
    }

    private GridRange toGridRange(World world, WorldAabb aabb) {
        int minX = (int) Math.floor(aabb.minX());
        int maxX = (int) Math.floor(aabb.maxX());
        int minY = (int) Math.floor(aabb.minY());
        int maxY = (int) Math.floor(aabb.maxY());
        int minZ = (int) Math.floor(aabb.minZ());
        int maxZ = (int) Math.floor(aabb.maxZ());

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return GridRange.INVALID;
        }

        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1;
        minY = clampInt(minY, worldMinY, worldMaxY);
        maxY = clampInt(maxY, worldMinY, worldMaxY);

        return new GridRange(minX, maxX, minY, maxY, minZ, maxZ, true);
    }

    private ColumnFluid findColumnFluid(World world,
                                        int x,
                                        int z,
                                        int minY,
                                        int maxY,
                                        float bodyBottomY) {
        int worldMaxY = world.getMaxHeight() - 1;

        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            FluidMedium medium = FluidMedium.fromLiquidMaterial(block.getType());
            if (medium == null) {
                continue;
            }

            float surface = fluidSurfaceY(block, y);
            if (surface <= bodyBottomY + 1.0e-3f) {
                continue;
            }

            if (Math.abs(surface - (y + 1.0f)) < 1.0e-4f) {
                float upwardSurface = searchSurfaceUp(world, x, y, z, medium.id, worldMaxY);
                if (upwardSurface > surface) {
                    surface = upwardSurface;
                }
            }

            return new ColumnFluid(medium.id, surface);
        }
        return null;
    }

    private float searchSurfaceUp(World world, int x, int startY, int z, byte mediumId, int worldMaxY) {
        int upper = Math.min(worldMaxY, startY + MAX_SURFACE_SEARCH_UP);
        for (int y = startY + 1; y <= upper; y++) {
            Block block = world.getBlockAt(x, y, z);
            FluidMedium medium = FluidMedium.fromLiquidMaterial(block.getType());
            if (medium != null && medium.id == mediumId) {
                continue;
            }

            Block below = world.getBlockAt(x, y - 1, z);
            FluidMedium belowMedium = FluidMedium.fromLiquidMaterial(below.getType());
            if (belowMedium != null && belowMedium.id == mediumId) {
                return fluidSurfaceY(below, y - 1);
            }
            break;
        }
        return INVALID_SURFACE;
    }

    private Vec3 estimateFluidFlow(World world, float cx, float surfaceY, float cz, int mediumId) {
        int x = (int) Math.floor(cx);
        int y = (int) Math.floor(surfaceY - 0.01f);
        int z = (int) Math.floor(cz);

        float west = sampleSurfaceAt(world, x - 1, y, z, mediumId);
        float east = sampleSurfaceAt(world, x + 1, y, z, mediumId);
        float north = sampleSurfaceAt(world, x, y, z - 1, mediumId);
        float south = sampleSurfaceAt(world, x, y, z + 1, mediumId);

        Vec3 flow = new Vec3(west - east, 0f, north - south);
        float lenSq = flow.getX() * flow.getX() + flow.getZ() * flow.getZ();
        if (lenSq <= 1.0e-6f) {
            return Vec3.sZero();
        }

        float invLen = (float) (1.0d / Math.sqrt(lenSq));
        flow.set(flow.getX() * invLen * FLOW_STRENGTH, 0f, flow.getZ() * invLen * FLOW_STRENGTH);
        return flow;
    }

    private float sampleSurfaceAt(World world, int x, int y, int z, int mediumId) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int baseY = clampInt(y, minY, maxY);

        for (int dy = 0; dy <= 1; dy++) {
            int probeY = clampInt(baseY - dy, minY, maxY);
            Block block = world.getBlockAt(x, probeY, z);
            FluidMedium medium = FluidMedium.fromLiquidMaterial(block.getType());
            if (medium == null || medium.id != (byte) mediumId) {
                continue;
            }
            return fluidSurfaceY(block, probeY);
        }

        return baseY;
    }

    private ForceProfile forceProfile(FluidMedium medium, float coverage) {
        float mediumScale = COVERAGE_BASE + COVERAGE_SCALE * coverage;
        return new ForceProfile(
                mediumBuoyancyFactor(medium) * mediumScale,
                mediumLinearDrag(medium) * mediumScale,
                mediumAngularDrag(medium) * mediumScale
        );
    }

    private float mediumBuoyancyFactor(FluidMedium medium) {
        float density = medium == FluidMedium.LAVA ? lavaDensity : waterDensity;
        return Math.max(0.1f, density / 72.0f) * buoyancyScale;
    }

    private float mediumLinearDrag(FluidMedium medium) {
        return medium == FluidMedium.LAVA ? lavaLinearDrag : waterLinearDrag;
    }

    private float mediumAngularDrag(FluidMedium medium) {
        return medium == FluidMedium.LAVA ? lavaAngularDrag : waterAngularDrag;
    }

    private static float fluidSurfaceY(Block block, int blockY) {
        double fill = 1.0d;
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Levelled levelled) {
                int lvl = levelled.getLevel();
                if (lvl < 8) {
                    fill = (8.0d - lvl) / 8.0d;
                }
            }
        } catch (Throwable ignored) {
            // Fallback to full block.
        }

        if (fill < 0d) fill = 0d;
        if (fill > 1d) fill = 1d;
        return (float) (blockY + fill);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private static final class SnapshotBuilder {
        private final ArrayList<Integer> ids;
        private final ArrayList<Byte> mediums;
        private final ArrayList<Float> surfaces;
        private final ArrayList<Float> coverages;
        private final ArrayList<Float> centersX;
        private final ArrayList<Float> centersZ;
        private final ArrayList<Float> flowsX;
        private final ArrayList<Float> flowsY;
        private final ArrayList<Float> flowsZ;

        private SnapshotBuilder(int initialCapacity) {
            this.ids = new ArrayList<>(initialCapacity);
            this.mediums = new ArrayList<>(initialCapacity);
            this.surfaces = new ArrayList<>(initialCapacity);
            this.coverages = new ArrayList<>(initialCapacity);
            this.centersX = new ArrayList<>(initialCapacity);
            this.centersZ = new ArrayList<>(initialCapacity);
            this.flowsX = new ArrayList<>(initialCapacity);
            this.flowsY = new ArrayList<>(initialCapacity);
            this.flowsZ = new ArrayList<>(initialCapacity);
        }

        private void add(int bodyId, FluidProbe probe) {
            ids.add(bodyId);
            mediums.add((byte) probe.mediumId());
            surfaces.add(probe.surfaceY());
            coverages.add(probe.coverage());
            centersX.add(probe.centerX());
            centersZ.add(probe.centerZ());
            flowsX.add(probe.flowX());
            flowsY.add(probe.flowY());
            flowsZ.add(probe.flowZ());
        }

        private FluidContactSnapshot build() {
            int n = ids.size();
            if (n == 0) {
                return FluidContactSnapshot.EMPTY;
            }

            int[] bodyIds = new int[n];
            byte[] mediumIds = new byte[n];
            float[] surfaceHeights = new float[n];
            float[] areaFractions = new float[n];
            float[] centerXs = new float[n];
            float[] centerZs = new float[n];
            float[] flowXs = new float[n];
            float[] flowYs = new float[n];
            float[] flowZs = new float[n];

            for (int i = 0; i < n; i++) {
                bodyIds[i] = ids.get(i);
                mediumIds[i] = mediums.get(i);
                surfaceHeights[i] = finiteOrZero(surfaces.get(i));
                areaFractions[i] = clamp01(coverages.get(i));
                centerXs[i] = centersX.get(i);
                centerZs[i] = centersZ.get(i);
                flowXs[i] = flowsX.get(i);
                flowYs[i] = flowsY.get(i);
                flowZs[i] = flowsZ.get(i);
            }

            return new FluidContactSnapshot(bodyIds, mediumIds, surfaceHeights, areaFractions, centerXs, centerZs, flowXs, flowYs, flowZs);
        }
    }

    private static final class ForceScratch {
        private final RVec3 surfacePoint = new RVec3();
        private final Vec3 surfaceNormal = new Vec3(0f, 1f, 0f);
        private final Vec3 flowVector = new Vec3();
    }

    private record WorldAabb(double minX,
                             double maxX,
                             double minY,
                             double maxY,
                             double minZ,
                             double maxZ,
                             float bodyBottomY) {
    }

    private record GridRange(int minX,
                             int maxX,
                             int minY,
                             int maxY,
                             int minZ,
                             int maxZ,
                             boolean valid) {
        private static final GridRange INVALID = new GridRange(0, -1, 0, -1, 0, -1, false);
    }

    private record ForceProfile(float buoyancy, float linearDrag, float angularDrag) {}

    private record ColumnFluid(byte mediumId, float surfaceY) {}

    private record FluidProbe(int mediumId,
                              float surfaceY,
                              float coverage,
                              float centerX,
                              float centerZ,
                              float flowX,
                              float flowY,
                              float flowZ) {}
}
