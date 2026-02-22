package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.ConstBodyLockInterface;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;

import java.util.Objects;

final class InertiaBuoyancyNarrowPhase {
    private final PhysicsWorld physicsWorld;
    private final ThreadLocal<RVec3> tempSurfacePos = ThreadLocal.withInitial(RVec3::new);
    private final ThreadLocal<Vec3> tempSurfaceNormal = ThreadLocal.withInitial(() -> new Vec3(0.0f, 1.0f, 0.0f));
    private final ThreadLocal<Vec3> tempFluidVelocity = ThreadLocal.withInitial(Vec3::new);

    InertiaBuoyancyNarrowPhase(PhysicsWorld physicsWorld) {
        this.physicsWorld = Objects.requireNonNull(physicsWorld, "physicsWorld");
    }

    void applyForces(ConstBodyLockInterface lockInterface, float deltaTime, InertiaBuoyancyDataStore dataStore) {
        Objects.requireNonNull(lockInterface, "lockInterface");
        Objects.requireNonNull(dataStore, "dataStore");

        BodyInterface bodyInterface = physicsWorld.getPhysicsSystem().getBodyInterfaceNoLock();
        Vec3 gravity = physicsWorld.getPhysicsSystem().getGravity();

        RVec3 surfacePos = tempSurfacePos.get();
        Vec3 surfaceNorm = tempSurfaceNormal.get();
        Vec3 fluidVel = tempFluidVelocity.get();

        for (int i = 0; i < dataStore.getCount(); ++i) {
            int bodyId = dataStore.bodyIds[i];
            if (bodyId == 0 || !bodyInterface.isAdded(bodyId)) {
                continue;
            }

            try (BodyLockWrite lock = new BodyLockWrite(lockInterface, bodyId)) {
                Body body = lock.getBody();
                if (body == null || !body.isActive()) {
                    continue;
                }

                surfacePos.set(dataStore.waterCenterX[i], dataStore.surfaceHeights[i], dataStore.waterCenterZ[i]);
                surfaceNorm.set(0.0f, 1.0f, 0.0f);
                fluidVel.set(dataStore.flowX[i], dataStore.flowY[i], dataStore.flowZ[i]);

                InertiaFluidType type = dataStore.fluidTypes[i];
                float buoyancyFactor = type == InertiaFluidType.LAVA ? 2.5f : 1.1f;
                float linearDrag = type == InertiaFluidType.LAVA ? 5.0f : 0.5f;
                float angularDrag = type == InertiaFluidType.LAVA ? 2.0f : 0.05f;

                body.applyBuoyancyImpulse(
                        surfacePos,
                        surfaceNorm,
                        buoyancyFactor,
                        linearDrag,
                        angularDrag,
                        fluidVel,
                        gravity,
                        deltaTime
                );
            }
        }
    }
}
