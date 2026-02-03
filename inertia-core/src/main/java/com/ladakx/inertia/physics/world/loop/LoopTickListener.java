package com.ladakx.inertia.physics.world.loop;

public interface LoopTickListener {
    void onTickStart(long tickNumber);
    void onTickEnd(long tickNumber, long durationNanos, int activeBodies, int totalBodies, int staticBodies, int maxBodies);
}