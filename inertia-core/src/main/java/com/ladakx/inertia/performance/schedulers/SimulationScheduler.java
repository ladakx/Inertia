package com.ladakx.inertia.performance.schedulers;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.bullet.space.SpaceManager;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler for handling simulation tasks at a fixed rate.
 */
public class SimulationScheduler extends AbstractScheduler {

    private final SpaceManager spaceManager;
    private final int tickRate;

    /**
     * Constructor to initialize the SimulationScheduler.
     * @param spaceManager The SpaceManager instance to manage Minecraft spaces.
     */
    public SimulationScheduler(SpaceManager spaceManager) {
        this.spaceManager = spaceManager;
        this.tickRate = 20;

        start();
    }

    /**
     * Start the simulation scheduler.
     */
    @Override
    public void start() {
        // Check if simulation is enabled
        if (!InertiaPlugin.getPConfig().SIMULATION.enable) return;

        // Schedule the task to run at a fixed rate
        timerService.scheduleAtFixedRate(this::process, 1000, 1000 / tickRate, TimeUnit.MILLISECONDS);
    }

    /**
     * Process the simulation tasks.
     */
    @Override
    protected void process() {
        for (MinecraftSpace space : spaceManager.getAll()) {
            space.stepSimulation();
        }
    }

    /**
     * Stop the simulation scheduler.
     */
    @Override
    public void stop() {
        if (!timerService.isShutdown()) {
            timerService.shutdown();
        }
    }
}