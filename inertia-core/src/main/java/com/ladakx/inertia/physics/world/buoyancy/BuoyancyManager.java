package com.ladakx.inertia.physics.world.buoyancy;

import com.github.stephengold.joltjni.readonly.ConstBodyLockInterface;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class BuoyancyManager {
    private final PhysicsWorld physicsWorld;
    private final BuoyancyBroadPhase broadPhase;
    private final BuoyancyNarrowPhase narrowPhase;
    private BuoyancyDataStore fillingBuffer;
    private final AtomicReference<BuoyancyDataStore> publishedBuffer;
    private BuoyancyDataStore readingBuffer;

    public BuoyancyManager(PhysicsWorld physicsWorld) {
        this.physicsWorld = Objects.requireNonNull(physicsWorld, "physicsWorld");
        this.broadPhase = new BuoyancyBroadPhase(physicsWorld.getWorldBukkit());
        this.narrowPhase = new BuoyancyNarrowPhase(physicsWorld);
        this.fillingBuffer = new BuoyancyDataStore(256);
        this.publishedBuffer = new AtomicReference<>(new BuoyancyDataStore(256));
        this.readingBuffer = new BuoyancyDataStore(256);
    }

    public void updateFluidStates(Collection<AbstractPhysicsBody> bodies) {
        Objects.requireNonNull(bodies, "bodies");
        fillingBuffer.clear();
        broadPhase.findPotentialFluidContacts(bodies, fillingBuffer);
        fillingBuffer = publishedBuffer.getAndSet(fillingBuffer);
    }

    public void applyBuoyancyForces(float deltaTime) {
        BuoyancyDataStore latestBuffer = publishedBuffer.getAndSet(readingBuffer);
        readingBuffer = latestBuffer;
        if (readingBuffer.getCount() == 0) {
            return;
        }
        ConstBodyLockInterface lockInterface = physicsWorld.getPhysicsSystem().getBodyLockInterfaceNoLock();
        narrowPhase.applyForces(lockInterface, deltaTime, readingBuffer);
    }
}
