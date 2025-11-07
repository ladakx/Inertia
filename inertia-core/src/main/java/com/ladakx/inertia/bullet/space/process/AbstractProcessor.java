package com.ladakx.inertia.bullet.space.process;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.space.MinecraftSpace;

import java.util.concurrent.ExecutorService;

public abstract class AbstractProcessor {

    // ***********************
    // Space
    protected final MinecraftSpace space;

    // ***********************
    // Performance
    protected final ExecutorService simulationThreadPool;

    public AbstractProcessor(MinecraftSpace space) {
        this.space = space;
        this.simulationThreadPool = InertiaPlugin.getSimulationThreadPool().getExecutorService();
    }

    public abstract void step();
    public abstract long getMSPT();
}