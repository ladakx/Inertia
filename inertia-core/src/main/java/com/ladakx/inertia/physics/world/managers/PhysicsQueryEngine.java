package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseQuery;
import com.ladakx.inertia.api.interaction.PhysicsInteraction;
import com.ladakx.inertia.api.interaction.RaycastHit;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PhysicsQueryEngine implements PhysicsInteraction {

    private final PhysicsWorld physicsWorld;
    private final PhysicsSystem physicsSystem;
    private final PhysicsObjectManager objectManager;

    public PhysicsQueryEngine(PhysicsWorld physicsWorld, PhysicsSystem physicsSystem, PhysicsObjectManager objectManager) {
        this.physicsWorld = physicsWorld;
        this.physicsSystem = physicsSystem;
        this.objectManager = objectManager;
    }

    @Override
    public @Nullable RaycastHit raycast(@NotNull Location start, @NotNull Vector direction, double distance) {
        // Convert Start Location to Jolt Space
        RVec3 startVec = physicsWorld.toJolt(start);

        // Direction is relative, usually doesn't need origin shift, but math is:
        // RayStart(Jolt) + Direction * fraction -> HitPoint(Jolt)
        Vector dir = direction.clone().normalize().multiply(distance);
        Vec3 dirVec = new Vec3((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());

        RRayCast ray = new RRayCast(startVec, dirVec);
        ClosestHitCastRayCollector collector = new ClosestHitCastRayCollector();
        RayCastSettings settings = new RayCastSettings();

        physicsSystem.getNarrowPhaseQuery().castRay(ray, settings, collector);

        if (collector.hadHit()) {
            RayCastResult hit = collector.getHit();
            ConstBodyLockInterfaceLocking bli = physicsSystem.getBodyLockInterface();
            try (BodyLockRead lock = new BodyLockRead(bli, hit.getBodyId())) {
                if (lock.succeeded()) {
                    ConstBody body = lock.getBody();
                    AbstractPhysicsBody obj = objectManager.getByVa(body.targetVa());
                    if (obj != null) {
                        // Calculate hit position in Jolt Space
                        RVec3 hitPosJolt = ray.getPointOnRay(hit.getFraction());
                        // Convert Jolt Hit Pos to Bukkit Location
                        Vector hitPosBukkit = physicsWorld.toBukkitVec(hitPosJolt);

                        return new RaycastHit(obj, hitPosBukkit, hit.getFraction());
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @NotNull List<RaycastHit> raycastAll(@NotNull Location start, @NotNull Vector direction, double distance) {
        // Convert Start Location to Jolt Space
        RVec3 startVec = physicsWorld.toJolt(start);

        Vector dir = direction.clone().normalize().multiply(distance);
        Vec3 dirVec = new Vec3((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());

        RRayCast ray = new RRayCast(startVec, dirVec);
        AllHitCastRayCollector collector = new AllHitCastRayCollector();
        RayCastSettings settings = new RayCastSettings();

        physicsSystem.getNarrowPhaseQuery().castRay(ray, settings, collector);

        List<RayCastResult> hits = collector.getHits();
        if (hits.isEmpty()) return Collections.emptyList();
        hits.sort(Comparator.comparingDouble(RayCastResult::getFraction));

        List<RaycastHit> results = new ArrayList<>();
        ConstBodyLockInterfaceLocking bli = physicsSystem.getBodyLockInterface();

        for (RayCastResult hit : hits) {
            try (BodyLockRead lock = new BodyLockRead(bli, hit.getBodyId())) {
                if (lock.succeeded()) {
                    ConstBody body = lock.getBody();
                    AbstractPhysicsBody obj = objectManager.getByVa(body.targetVa());
                    if (obj != null) {
                        // Calculate hit position in Jolt Space
                        RVec3 hitPosJolt = ray.getPointOnRay(hit.getFraction());
                        // Convert Jolt Hit Pos to Bukkit Location
                        Vector hitPosBukkit = physicsWorld.toBukkitVec(hitPosJolt);

                        results.add(new RaycastHit(obj, hitPosBukkit, hit.getFraction()));
                    }
                }
            }
        }
        return results;
    }

    @Override
    public @NotNull Collection<InertiaPhysicsBody> getOverlappingSphere(@NotNull Location center, double radius) {
        // Convert Center to Jolt Space
        RVec3 centerVec = physicsWorld.toJolt(center);
        Vec3 centerVecF = new Vec3((float)centerVec.xx(), (float)centerVec.yy(), (float)centerVec.zz());

        try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector()) {
            physicsSystem.getBroadPhaseQuery().collideSphere(centerVecF, (float) radius, collector);

            if (collector.getHits().length == 0) return Collections.emptyList();

            Set<InertiaPhysicsBody> bodies = new HashSet<>();
            ConstBodyLockInterfaceLocking bli = physicsSystem.getBodyLockInterface();
            int[] hitIds = collector.getHits();

            for (int bodyId : hitIds) {
                try (BodyLockRead lock = new BodyLockRead(bli, bodyId)) {
                    if (lock.succeeded()) {
                        ConstBody body = lock.getBody();
                        AbstractPhysicsBody obj = objectManager.getByVa(body.targetVa());
                        if (obj != null) {
                            bodies.add(obj);
                        }
                    }
                }
            }
            return bodies;
        }
    }

    @Override
    public void createExplosion(@NotNull Location center, float force, float radius) {
        // Convert Center to Jolt Space
        RVec3 centerVec = physicsWorld.toJolt(center);
        Vec3 origin = new Vec3((float)centerVec.xx(), (float)centerVec.yy(), (float)centerVec.zz());

        createExplosionInternal(origin, force, radius);
    }

    private void createExplosionInternal(@NotNull Vec3 origin, float force, float radius) {
        if (force <= 0f || radius <= 0f) return;

        final float scaledForce = force * 500;
        final float radiusSq = radius * radius;
        BodyInterface bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        ConstBroadPhaseQuery broadPhase = physicsSystem.getBroadPhaseQuery();

        try (AllHitCollideShapeBodyCollector collector = new AllHitCollideShapeBodyCollector()) {
            broadPhase.collideSphere(origin, radius, collector);
            int[] hits = collector.getHits();
            if (hits == null || hits.length == 0) return;

            for (int bodyId : hits) {
                if (!isValidExplosionTarget(bodyInterface, bodyId)) continue;

                RVec3 com = bodyInterface.getCenterOfMassPosition(bodyId);
                double dx = com.xx() - origin.getX();
                double dy = com.yy() - origin.getY();
                double dz = com.zz() - origin.getZ();

                float dxF = (float) dx;
                float dyF = (float) dy;
                float dzF = (float) dz;

                float distSq = dxF*dxF + dyF*dyF + dzF*dzF;
                if (distSq <= 1.0e-6f || distSq > radiusSq) continue;

                float dist = (float) Math.sqrt(distSq);
                float factor = 1.0f - (dist / radius);

                if (factor > 0f) {
                    Vec3 impulse = new Vec3(dxF, dyF, dzF).normalized();
                    impulse.scaleInPlace(scaledForce * factor);
                    bodyInterface.activateBody(bodyId);
                    bodyInterface.addImpulse(bodyId, impulse);
                }
            }
        }
    }

    private boolean isValidExplosionTarget(BodyInterface bi, int bodyId) {
        return bi.getMotionType(bodyId) == EMotionType.Dynamic && !bi.isSensor(bodyId);
    }
}