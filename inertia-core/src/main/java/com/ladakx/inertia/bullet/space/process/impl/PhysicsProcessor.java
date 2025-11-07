// 3. PhysicsProcessor
package com.ladakx.inertia.bullet.space.process.impl;

import com.ladakx.inertia.bullet.bodies.element.PhysicsElement;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.bullet.space.process.AbstractProcessor;

/**
 * The physics processor for the space
 */
public class PhysicsProcessor extends AbstractProcessor {

    // ***********************
    // Variables
    protected volatile long lastStep = System.nanoTime();
    protected volatile long mspt = 0L;

    /**
     * Create a new physics processor
     * @param space the space to process
     */
    public PhysicsProcessor(MinecraftSpace space) {
        super(space);
    }

    /**
     * Execute a physics step
     */
    @Override
    public void step() {
        long start = System.nanoTime();

        float delta = (start - lastStep) / 1_000_000_000f;
        lastStep = start;

        space.update(delta);

        for (PhysicsElement element : space.getPhysicsElements()) {
            element.update();
        }

        long end = System.nanoTime();
        mspt = (end - start) / 1_000_000L;
    }

    @Override
    public long getMSPT() {
        return mspt;
    }
}