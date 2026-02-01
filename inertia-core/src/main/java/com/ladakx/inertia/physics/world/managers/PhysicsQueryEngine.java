package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseQuery;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.common.utils.MiscUtils;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PhysicsQueryEngine {

    private final PhysicsSystem physicsSystem;
    private final PhysicsObjectManager objectManager;

    public PhysicsQueryEngine(PhysicsSystem physicsSystem, PhysicsObjectManager objectManager) {
        this.physicsSystem = physicsSystem;
        this.objectManager = objectManager;
    }

    public void createExplosion(@NotNull Vec3 origin, float force, float radius) {
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
                Vec3 offset = new Vec3(
                        (float) com.xx() - origin.getX(),
                        (float) com.yy() - origin.getY(),
                        (float) com.zz() - origin.getZ()
                );

                float distSq = offset.lengthSq();
                if (distSq <= 1.0e-6f || distSq > radiusSq) continue;

                float dist = (float) Math.sqrt(distSq);
                float factor = 1.0f - (dist / radius);
                
                if (factor > 0f) {
                    Vec3 impulse = offset.normalized();
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

    public List<PhysicsWorld.RaycastResult> raycastEntity(@NotNull Location startPoint, @NotNull Vector direction, double maxDistance) {
        Vector endOffset = direction.clone().normalize().multiply(maxDistance);
        Vector endPoint = startPoint.clone().add(endOffset).toVector();

        AllHitRayCastBodyCollector collector = new AllHitRayCastBodyCollector();
        physicsSystem.getBroadPhaseQuery().castRay(
                new RayCast(ConvertUtils.toVec3(startPoint), ConvertUtils.toVec3(endOffset)),
                collector
        );

        List<PhysicsWorld.RaycastResult> results = new ArrayList<>();
        
        // Используем BodyLockInterface для безопасного чтения тел в многопоточной среде, 
        // если этот метод вызывается асинхронно
        var bli = physicsSystem.getBodyLockInterfaceNoLock();

        for (BroadPhaseCastResult hit : collector.getHits()) {
            try (BodyLockRead lock = new BodyLockRead(bli, hit.getBodyId())) {
                if (lock.succeeded()) {
                    ConstBody body = lock.getBody();
                    AbstractPhysicsBody obj = objectManager.getByVa(body.targetVa());
                    
                    if (obj != null) {
                        Vector hitPos0 = MiscUtils.lerpVec(startPoint.toVector(), endPoint, hit.getFraction());
                        RVec3 hitPos = ConvertUtils.toRVec3(hitPos0);
                        results.add(new PhysicsWorld.RaycastResult(body.targetVa(), hitPos));
                    }
                }
            }
        }
        return results;
    }
}