package com.ladakx.inertia.bullet;

import com.ladakx.inertia.bullet.cache.CacheManager;
import com.ladakx.inertia.bullet.space.SpaceManager;
import com.ladakx.inertia.debug.DebugBlockManager;
import com.ladakx.inertia.performance.schedulers.PhysicsScheduler;
import com.ladakx.inertia.performance.schedulers.SimulationScheduler;

public class BulletManager {

    // ************************************
    // General
    private final SpaceManager spaceManager;
    private final CacheManager cacheManager;

    // ************************************
    // Debug Block Manager
    private final DebugBlockManager debugBlockManager;

    // ************************************
    // Timers
    private PhysicsScheduler physicsScheduler;
    private SimulationScheduler simulationScheduler;

    // ************************************
    // Constructors
    public BulletManager() {
        this.cacheManager = new CacheManager();
        this.spaceManager = new SpaceManager();

        this.debugBlockManager = new DebugBlockManager();
    }

    public void init() {
        spaceManager.loadSpaces();

        // init schedulers
        this.physicsScheduler = new PhysicsScheduler(spaceManager);
        this.simulationScheduler = new SimulationScheduler(spaceManager);
    }

    public void stopSchedulers() {
        physicsScheduler.stop();
        simulationScheduler.stop();
    }

    // ************************************
    // Getter service
    public SpaceManager getSpaceManager() {
        return spaceManager;
    }
    public CacheManager getCacheManager() {
        return cacheManager;
    }
    public DebugBlockManager getDebugBlockManager() {
        return debugBlockManager;
    }

    // ************************************
    // Getter Timers
    public PhysicsScheduler getPhysicsScheduler() {
        return physicsScheduler;
    }

    public SimulationScheduler getSimulationScheduler() {
        return simulationScheduler;
    }
}
