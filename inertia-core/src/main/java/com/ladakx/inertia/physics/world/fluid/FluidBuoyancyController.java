package com.ladakx.inertia.physics.world.fluid;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FluidBuoyancyController {
    private static final float MIN_SUBMERSION_TO_SIMULATE = 0.05f;
    private static final float SAMPLE_Y_EPSILON = 0.05f;

    private final PhysicsWorld physicsWorld;
    private final AtomicReference<FluidContactSnapshot> snapshotRef = new AtomicReference<>(FluidContactSnapshot.EMPTY);

    public FluidBuoyancyController(@NotNull PhysicsWorld physicsWorld) {
        this.physicsWorld = Objects.requireNonNull(physicsWorld, "physicsWorld");
    }

    public void refreshContacts(@NotNull Collection<AbstractPhysicsBody> activeBodies) {
        Objects.requireNonNull(activeBodies, "activeBodies");
        World world = physicsWorld.getWorldBukkit();
        RVec3 origin = physicsWorld.getOrigin();

        ArrayList<Integer> ids = new ArrayList<>(Math.min(256, activeBodies.size()));
        ArrayList<Byte> mediums = new ArrayList<>(Math.min(256, activeBodies.size()));
        ArrayList<Float> submersions = new ArrayList<>(Math.min(256, activeBodies.size()));

        for (AbstractPhysicsBody obj : activeBodies) {
            if (obj == null || !obj.isValid()) {
                continue;
            }

            Body body = obj.getBody();
            if (body == null) {
                continue;
            }
            if (body.getMotionType() != EMotionType.Dynamic) {
                continue;
            }

            RVec3 posLocal = body.getPosition();
            double centerX = posLocal.xx() + origin.xx();
            double centerY = posLocal.yy() + origin.yy();
            double centerZ = posLocal.zz() + origin.zz();

            ConstAaBox localBounds = body.getShape().getLocalBounds();
            Vec3 min = localBounds.getMin();
            Vec3 max = localBounds.getMax();
            double halfX = Math.max(0.05d, (max.getX() - min.getX()) * 0.5d);
            double halfY = Math.max(0.05d, (max.getY() - min.getY()) * 0.5d);
            double halfZ = Math.max(0.05d, (max.getZ() - min.getZ()) * 0.5d);

            double leftX = centerX - halfX;
            double rightX = centerX + halfX;
            double bottomY = centerY - halfY;
            double topY = centerY + halfY;
            double frontZ = centerZ - halfZ;
            double backZ = centerZ + halfZ;

            SampleResult sample = sampleAabbLiquids(world, leftX, rightX, bottomY, topY, frontZ, backZ);
            if (sample.mediumId == -1 || sample.submersion < MIN_SUBMERSION_TO_SIMULATE) {
                continue;
            }

            ids.add(body.getId());
            mediums.add((byte) sample.mediumId);
            submersions.add(sample.submersion);
        }

        int n = ids.size();
        int[] bodyIds = new int[n];
        byte[] mediumIds = new byte[n];
        float[] submersion = new float[n];
        for (int i = 0; i < n; i++) {
            bodyIds[i] = ids.get(i);
            mediumIds[i] = mediums.get(i);
            submersion[i] = submersions.get(i);
        }

        snapshotRef.set(n == 0 ? FluidContactSnapshot.EMPTY : new FluidContactSnapshot(bodyIds, mediumIds, submersion));
    }

    public void applyForces(float dtSeconds) {
        if (dtSeconds <= 0f) {
            return;
        }

        FluidContactSnapshot snapshot = snapshotRef.get();
        if (snapshot.size() == 0) {
            return;
        }

        PhysicsSystem physicsSystem = physicsWorld.getPhysicsSystem();
        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        Vec3 gravity = physicsSystem.getGravity();
        var bli = physicsSystem.getBodyLockInterfaceNoLock();

        for (int i = 0; i < snapshot.size(); i++) {
            int bodyId = snapshot.bodyIds[i];
            FluidMedium medium = FluidMedium.fromId(snapshot.mediumIds[i]);
            float sub = clamp01(snapshot.submersion[i]);

            try (BodyLockRead lock = new BodyLockRead(bli, bodyId)) {
                if (!lock.succeeded()) {
                    continue;
                }
                Body body = (Body) lock.getBody();
                if (body.getMotionType() != EMotionType.Dynamic) {
                    continue;
                }

                MotionProperties props = body.getMotionProperties();
                if (props == null) {
                    continue;
                }
                float invMass = props.getInverseMass();
                if (invMass <= 0f) {
                    continue;
                }
                float mass = 1.0f / invMass;

                float buoyancyScale = medium.buoyancy * sub * mass * dtSeconds;
                Vec3 buoyancyImpulse = new Vec3(
                        -gravity.getX() * buoyancyScale,
                        -gravity.getY() * buoyancyScale,
                        -gravity.getZ() * buoyancyScale
                );
                bodyInterface.addImpulse(bodyId, buoyancyImpulse);

                Vec3 v = bodyInterface.getLinearVelocity(bodyId);
                float linearScale = medium.linearDamping * sub * mass * dtSeconds;
                Vec3 linearDragImpulse = new Vec3(
                        -v.getX() * linearScale,
                        -v.getY() * linearScale,
                        -v.getZ() * linearScale
                );
                bodyInterface.addImpulse(bodyId, linearDragImpulse);

                Vec3 w = bodyInterface.getAngularVelocity(bodyId);
                float angularScale = medium.angularDamping * sub * mass * dtSeconds;
                Vec3 angularDragTorque = new Vec3(
                        -w.getX() * angularScale,
                        -w.getY() * angularScale,
                        -w.getZ() * angularScale
                );
                bodyInterface.addTorque(bodyId, angularDragTorque);
            } catch (Throwable ignored) {
                // Bodies can disappear between scans; buoyancy is best-effort.
            }
        }
    }

    private record SampleResult(int mediumId, float submersion) {}

    private SampleResult sampleAabbLiquids(World world,
                                          double minX, double maxX,
                                          double minY, double maxY,
                                          double minZ, double maxZ) {
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight() - 1;

        double midX = (minX + maxX) * 0.5d;
        double midZ = (minZ + maxZ) * 0.5d;

        double yLow = Math.min(maxY - SAMPLE_Y_EPSILON, minY + SAMPLE_Y_EPSILON);
        double yHigh = Math.max(minY + SAMPLE_Y_EPSILON, maxY - SAMPLE_Y_EPSILON);

        double[] xs = new double[]{minX, midX, maxX};
        double[] zs = new double[]{minZ, midZ, maxZ};
        double[] ys = new double[]{yLow, (minY + maxY) * 0.5d, yHigh};

        int waterHits = 0;
        int lavaHits = 0;
        int samples = 0;

        for (double y : ys) {
            int by = clampToIntRange((int) Math.floor(y), minHeight, maxHeight);
            for (double x : xs) {
                int bx = (int) Math.floor(x);
                for (double z : zs) {
                    int bz = (int) Math.floor(z);
                    Block block = world.getBlockAt(bx, by, bz);
                    if (!block.isLiquid()) {
                        samples++;
                        continue;
                    }
                    FluidMedium medium = FluidMedium.fromLiquidMaterial(block.getType());
                    if (medium == FluidMedium.LAVA) {
                        lavaHits++;
                    } else {
                        waterHits++;
                    }
                    samples++;
                }
            }
        }

        if (samples <= 0) {
            return new SampleResult(-1, 0f);
        }

        int liquidHits = waterHits + lavaHits;
        if (liquidHits == 0) {
            return new SampleResult(-1, 0f);
        }

        int picked = lavaHits > waterHits ? FluidMedium.LAVA.id : FluidMedium.WATER.id;
        float sub = (float) liquidHits / (float) samples;
        return new SampleResult(picked, sub);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static int clampToIntRange(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}

