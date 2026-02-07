package com.ladakx.inertia.physics.world.terrain;

public interface PhysicsGenerator<T> {
    T generate(ChunkSnapshotData snapshot);
}
