package com.ladakx.inertia.physics.engine;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.configuration.dto.WorldsConfig;

public class JoltSystemFactory {

    public PhysicsSystem createSystem(WorldsConfig.WorldProfile settings) {
        // 1. Setup Layer Filters
        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(PhysicsLayers.NUM_OBJ_LAYERS);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_MOVING);
        ovoFilter.enableCollision(PhysicsLayers.OBJ_MOVING, PhysicsLayers.OBJ_STATIC);
        ovoFilter.disableCollision(PhysicsLayers.OBJ_STATIC, PhysicsLayers.OBJ_STATIC);

        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(PhysicsLayers.NUM_OBJ_LAYERS, PhysicsLayers.NUM_BP_LAYERS);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_MOVING, 0);
        layerMap.mapObjectToBroadPhaseLayer(PhysicsLayers.OBJ_STATIC, 0);

        ObjectVsBroadPhaseLayerFilter ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, PhysicsLayers.NUM_BP_LAYERS, ovoFilter, PhysicsLayers.NUM_OBJ_LAYERS);

        // 2. Initialize System
        PhysicsSystem sys = new PhysicsSystem();
        sys.init(
                settings.performance().maxBodies(),
                settings.performance().numBodyMutexes(),
                settings.performance().maxBodyPairs(),
                settings.performance().maxContactConstraints(),
                layerMap,
                ovbFilter,
                ovoFilter
        );

        // 3. Apply Settings
        PhysicsSettings physSettings = new PhysicsSettings();
        WorldsConfig.SolverSettings solverSettings = settings.solver();

        physSettings.setNumVelocitySteps(solverSettings.velocityIterations());
        physSettings.setNumPositionSteps(solverSettings.positionIterations());
        physSettings.setBaumgarte(solverSettings.baumgarte());
        physSettings.setSpeculativeContactDistance(solverSettings.speculativeContactDistance());
        physSettings.setPenetrationSlop(solverSettings.penetrationSlop());
        physSettings.setLinearCastThreshold(solverSettings.linearCastThreshold());
        physSettings.setLinearCastMaxPenetration(solverSettings.linearCastMaxPenetration());
        physSettings.setManifoldTolerance(solverSettings.manifoldTolerance());
        physSettings.setMaxPenetrationDistance(solverSettings.maxPenetrationDistance());
        physSettings.setConstraintWarmStart(solverSettings.constraintWarmStart());
        physSettings.setUseBodyPairContactCache(solverSettings.useBodyPairContactCache());
        physSettings.setUseLargeIslandSplitter(solverSettings.useLargeIslandSplitter());
        physSettings.setAllowSleeping(solverSettings.allowSleeping());
        physSettings.setDeterministicSimulation(solverSettings.deterministicSimulation());

        WorldsConfig.SleepSettings sleepSettings = settings.sleeping();
        physSettings.setTimeBeforeSleep(sleepSettings.timeBeforeSleep());
        physSettings.setPointVelocitySleepThreshold(sleepSettings.pointVelocityThreshold());

        sys.setPhysicsSettings(physSettings);
        sys.setGravity(settings.gravity());
        
        return sys;
    }
}