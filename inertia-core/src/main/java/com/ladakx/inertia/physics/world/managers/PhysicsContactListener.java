package com.ladakx.inertia.physics.world.managers;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.ladakx.inertia.api.events.PhysicsCollisionEvent;
import com.ladakx.inertia.api.events.PhysicsCollisionPayload;
import com.ladakx.inertia.api.events.PhysicsEventPayload;
import com.ladakx.inertia.core.api.body.ApiPhysicsBodyAdapter;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.entity.EntityPhysicsManager;
import com.ladakx.inertia.physics.events.PhysicsEventDispatcher;

import java.util.Objects;

public final class PhysicsContactListener extends CustomContactListener {
    private final PhysicsObjectManager objectManager;
    private final PhysicsSystem physicsSystem;
    private final EntityPhysicsManager entityPhysicsManager;
    private final ApiPhysicsBodyAdapter apiPhysicsBodyAdapter;
    private final PhysicsEventDispatcher eventDispatcher;

    public PhysicsContactListener(PhysicsObjectManager objectManager,
                                  PhysicsSystem physicsSystem,
                                  EntityPhysicsManager entityPhysicsManager,
                                  ApiPhysicsBodyAdapter apiPhysicsBodyAdapter,
                                  PhysicsEventDispatcher eventDispatcher) {
        this.objectManager = Objects.requireNonNull(objectManager, "objectManager");
        this.physicsSystem = Objects.requireNonNull(physicsSystem, "physicsSystem");
        this.entityPhysicsManager = Objects.requireNonNull(entityPhysicsManager, "entityPhysicsManager");
        this.apiPhysicsBodyAdapter = Objects.requireNonNull(apiPhysicsBodyAdapter, "apiPhysicsBodyAdapter");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
    }

    @Override
    public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
        AbstractPhysicsBody firstPhysicsBody = objectManager.getByVa(body1Va);
        AbstractPhysicsBody secondPhysicsBody = objectManager.getByVa(body2Va);
        if (firstPhysicsBody != null && secondPhysicsBody != null) {
            var firstBody = apiPhysicsBodyAdapter.adapt(firstPhysicsBody);
            var secondBody = apiPhysicsBodyAdapter.adapt(secondPhysicsBody);
            var firstLocation = firstBody.getLocation();
            var secondLocation = secondBody.getLocation();
            PhysicsCollisionPayload payload = new PhysicsCollisionPayload(
                    PhysicsEventPayload.SCHEMA_VERSION_V1,
                    Objects.requireNonNull(firstLocation.getWorld(), "firstLocation.world").getUID(),
                    firstBody.getBodyId(),
                    secondBody.getBodyId(),
                    (firstLocation.getX() + secondLocation.getX()) * 0.5d,
                    (firstLocation.getY() + secondLocation.getY()) * 0.5d,
                    (firstLocation.getZ() + secondLocation.getZ()) * 0.5d
            );
            eventDispatcher.dispatchAsync(new PhysicsCollisionEvent(payload), payload);
            return;
        }

        Body body1 = new Body(body1Va);
        Body body2 = new Body(body2Va);
        int body1Id = body1.getId();
        int body2Id = body2.getId();

        boolean body1Entity = entityPhysicsManager.isEntityProxy(body1Id);
        boolean body2Entity = entityPhysicsManager.isEntityProxy(body2Id);
        if (!body1Entity && !body2Entity) {
            return;
        }

        var bi = physicsSystem.getBodyInterfaceNoLock();
        boolean body1Sensor = body1Entity && bi.isSensor(body1Id);
        boolean body2Sensor = body2Entity && bi.isSensor(body2Id);
        entityPhysicsManager.handleDynamicContact(
                body1Id,
                body2Id,
                bi.getLinearVelocity(body1Id),
                bi.getLinearVelocity(body2Id),
                body1.getMotionProperties().getInverseMass() <= 0f ? 0f : 1f / body1.getMotionProperties().getInverseMass(),
                body2.getMotionProperties().getInverseMass() <= 0f ? 0f : 1f / body2.getMotionProperties().getInverseMass(),
                body1Sensor,
                body2Sensor
        );
    }
}
