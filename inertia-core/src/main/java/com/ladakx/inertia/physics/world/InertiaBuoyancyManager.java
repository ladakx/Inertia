package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.ConstBodyLockInterface;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class InertiaBuoyancyManager {
    private final PhysicsWorld physicsWorld;
    private final InertiaBuoyancyBroadPhase broadPhase;
    private final InertiaBuoyancyNarrowPhase narrowPhase;
    private InertiaBuoyancyDataStore fillingBuffer;
    private final AtomicReference<InertiaBuoyancyDataStore> publishedBuffer;
    private InertiaBuoyancyDataStore readingBuffer;

    InertiaBuoyancyManager(PhysicsWorld physicsWorld) {
        this.physicsWorld = Objects.requireNonNull(physicsWorld, "physicsWorld");
        this.broadPhase = new InertiaBuoyancyBroadPhase(physicsWorld.getWorldBukkit());
        this.narrowPhase = new InertiaBuoyancyNarrowPhase(physicsWorld);
        this.fillingBuffer = new InertiaBuoyancyDataStore(256);
        this.publishedBuffer = new AtomicReference<>(new InertiaBuoyancyDataStore(256));
        this.readingBuffer = new InertiaBuoyancyDataStore(256);
    }

    void updateFluidStates(Collection<AbstractPhysicsBody> bodies) {
        Objects.requireNonNull(bodies, "bodies");
        fillingBuffer.clear();
        broadPhase.findPotentialFluidContacts(bodies, fillingBuffer);
        fillingBuffer = publishedBuffer.getAndSet(fillingBuffer);
    }

    void applyBuoyancyForces(float deltaTime) {
        InertiaBuoyancyDataStore latestBuffer = publishedBuffer.getAndSet(readingBuffer);
        readingBuffer = latestBuffer;
        if (readingBuffer.getCount() == 0) {
            return;
        }
        ConstBodyLockInterface lockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock();
        narrowPhase.applyForces(lockInterface, deltaTime, readingBuffer);
    }
}
