package com.ladakx.inertia.bullet.space.process.impl;

import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.bullet.space.process.AbstractProcessor;

public class SimulationProcessor extends AbstractProcessor {

    // ***********************
    // Variables
    protected volatile boolean isStepping = false;
    protected volatile long mspt = 0L;

    public SimulationProcessor(MinecraftSpace space) {
        super(space);
    }

    @Override
    public void step() {
        if (isStepping) {
            return;
        }

        long startTime = System.nanoTime();

        if (space.isEmpty()) {
            mspt = 0L;
            return;
        }

        isStepping = true;

        space.stepGenerator();

        long endTime = System.nanoTime();
        mspt = (endTime - startTime) / 1_000_000L;
        isStepping = false;
    }

    @Override
    public long getMSPT() {
        return mspt;
    }
}