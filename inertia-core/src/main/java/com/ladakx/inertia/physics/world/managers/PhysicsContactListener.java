package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.ladakx.inertia.physics.entity.EntityPhysicsManager;

import java.util.Objects;

public final class PhysicsContactListener extends CustomContactListener {
    private final PhysicsObjectManager objectManager;
    private final PhysicsSystem physicsSystem;
    private final EntityPhysicsManager entityPhysicsManager;

    public PhysicsContactListener(PhysicsObjectManager objectManager,
                                  PhysicsSystem physicsSystem,
                                  EntityPhysicsManager entityPhysicsManager) {
        this.objectManager = Objects.requireNonNull(objectManager);
        this.physicsSystem = Objects.requireNonNull(physicsSystem);
        this.entityPhysicsManager = Objects.requireNonNull(entityPhysicsManager);
    }

    @Override
    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        if (objectManager.getByVa(body1Va) != null && objectManager.getByVa(body2Va) != null) {
            return;
        }

        Body body1 = new Body(body1Va);
        Body body2 = new Body(body2Va);
        int body1Id = body1.getId();
        int body2Id = body2.getId();

        if (!entityPhysicsManager.isEntityProxy(body1Id) && !entityPhysicsManager.isEntityProxy(body2Id)) {
            return;
        }

        var bodyInterface = physicsSystem.getBodyInterfaceNoLock();
        float invMass1 = bodyInterface.getInverseMass(body1Id);
        float invMass2 = bodyInterface.getInverseMass(body2Id);
        float mass1 = invMass1 <= 0f ? 0f : 1f / invMass1;
        float mass2 = invMass2 <= 0f ? 0f : 1f / invMass2;

        entityPhysicsManager.handleDynamicContact(
                body1Id,
                body2Id,
                bodyInterface.getCenterOfMassPosition(body1Id),
                bodyInterface.getCenterOfMassPosition(body2Id),
                bodyInterface.getLinearVelocity(body1Id),
                bodyInterface.getLinearVelocity(body2Id),
                mass1,
                mass2
        );
    }
}
