package com.ladakx.inertia.bullet.space;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.SolverType;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import jme3utilities.Validate;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.terrarian.ITerrainGenerator;
import com.ladakx.inertia.bullet.BulletManager;
import com.ladakx.inertia.bullet.generator.rayon.RayonGenerator;
import com.ladakx.inertia.api.events.PhysicsContactEvent;
import com.ladakx.inertia.bullet.space.process.impl.PhysicsProcessor;
import com.ladakx.inertia.bullet.space.process.impl.SimulationProcessor;
import com.ladakx.inertia.debug.HitboxRender;
import com.ladakx.inertia.files.config.PluginCFG;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.bullet.bodies.element.PhysicsElement;
import com.ladakx.inertia.bullet.bodies.terrarian.BlockRigidBody;
import com.ladakx.inertia.performance.pool.PhysicsThread;
import com.ladakx.inertia.performance.pool.SimulationThreadPool;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Represents a Minecraft world integrated with the JBullet physics engine.
 * Handles physics simulation and terrain generation.
 */
public class MinecraftSpace extends PhysicsSpace implements PhysicsCollisionListener {
    // ************************************
    // General final fields
    private final SpaceManager spaceManager;
    private final BulletManager bulletManager;

    // ************************************
    // Bukkit world
    private final World world;
    private final String worldName;

    // ************************************
    // Terrain
    private final ConcurrentHashMap<BlockPos, BlockRigidBody> terrainMap;
    private final ConcurrentHashMap<PhysicsCollisionObject, PhysicsElement> physicsElementMap;
    private Collection<PhysicsElement> physicsElementCollection;

    // ************************************
    // Generator
    private ITerrainGenerator generator;

    // ************************************
    // Performance
    private final SimulationThreadPool simulationThreadPool;
    private PhysicsThread physicsThread;

    // ************************************
    // Debug
    private final BossBar debugBar;
    private final String debugBarPlaceholder;

    // ************************************
    // Processors
    private final PhysicsProcessor physicsProcessor;
    private final SimulationProcessor simulationProcessor;

    // ************************************
    // Queue
    private final Queue<Runnable> physicsTasks = new ConcurrentLinkedQueue<>();

    /**
     * Constructs a new MinecraftSpace.
     *
     * @param world The Bukkit world associated with this space.
     * @param worldMax The maximum world boundaries.
     * @param worldMin The minimum world boundaries.
     * @param broadphaseType The broadphase collision detection algorithm.
     * @param solverType The type of solver used for physics calculations.
     */
    public MinecraftSpace(World world, PhysicsThread thread, Vector3f worldMax, Vector3f worldMin, BroadphaseType broadphaseType, SolverType solverType) {
        super(worldMin, worldMax, broadphaseType, solverType);

        // init general fields
        this.bulletManager = InertiaPlugin.getBulletManager();
        this.spaceManager = bulletManager.getSpaceManager();
        this.world = world;
        this.worldName = world.getName();

        // create debug
        this.debugBarPlaceholder = InertiaPlugin.getPConfig().GENERAL.DEBUG.debugPlaceholderBar;
        this.debugBar = BossBar.bossBar(Component.text(debugBarPlaceholder), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

        // create general
        this.simulationThreadPool = InertiaPlugin.getSimulationThreadPool();
        this.physicsThread = thread;

        this.terrainMap = new ConcurrentHashMap<>();
        this.physicsElementMap = new ConcurrentHashMap<>();
        this.physicsElementCollection = physicsElementMap.values();

        // settings world
        PluginCFG.Physics.Worlds.World settings = InertiaPlugin.getPConfig().PHYSICS.WORLDS.getWorld(world.getName());
        this.getSolverInfo().setNumIterations(settings.iterations);
        this.setGravity(settings.gravity);
        this.setAccuracy(settings.accuracy);

        this.setMaxSubSteps(InertiaPlugin.getPConfig().PHYSICS.tickRate);
        this.setMaxTimeStep(settings.maxTimeStep);

        this.setForceUpdateAllAabbs(false);
        this.initSolverInfo();

        // Set generator
        this.setGenerator();

        // Initialize processors
        this.physicsProcessor = new PhysicsProcessor(this);
        this.simulationProcessor = new SimulationProcessor(this);
    }

    /**
     * Updates the debug bar with the current physics statistics.
     */
    public void debug() {
        updateDebug(this.countCollisionObjects(), this.countVehicles(), getPhysicsMSPT(), getSimulationMSPT());
    }

    /**
     * Steps the simulation processor.
     */
    public void stepSimulation() {
        simulationThreadPool.execute(simulationProcessor::step);
    }

    /**
     * Steps the physics processor and updates the debug bar if necessary.
     */
    public void stepPhysics() {
        physicsThread.execute(() -> {
            // Debug bar
            if (!spaceManager.getPlayersDebug().isEmpty()) {
                debug();
            }

            // Execute physics tasks (Add/Remove objects)
            Runnable command;
            while ((command = physicsTasks.poll()) != null) {
                command.run();
            }

            // Physics Step
            physicsProcessor.step();

            // Render hitboxes
            if (InertiaPlugin.enableHitboxRender) {
                for (PhysicsElement physicsElement : getPhysicsElements()) {
                    HitboxRender.render(physicsElement.getRigidBody(), getWorld());
                }
//                for (BlockRigidBody blockRigidBody : getTerrainMap().values()) {
//                    HitboxRender.render(blockRigidBody, getWorld());
//                }
            }
        });
    }

    /**
     * Schedules a task to be executed in the physics thread.
     * to &laquo;Physics&raquo; thread. This method is thread-safe.
     */
    public void schedulePhysicsTask(Runnable task) {
        physicsTasks.add(task);
    }

    /**
     * Reloads the physics settings from the configuration.
     */
    public void reload() {
        PluginCFG.Physics.Worlds.World settings = InertiaPlugin.getPConfig().PHYSICS.WORLDS.getWorld(world.getName());
        if (settings == null) return;

        schedulePhysicsTask(() -> {
            this.setGravity(settings.gravity);
            this.setAccuracy(settings.accuracy);

            this.setMaxSubSteps(settings.maxSubSteps);
            this.setMaxTimeStep(settings.maxTimeStep);

            this.updateSolver();
            this.initSolverInfo();
        });
    }

    /**
     * Adds a physics element to the space.
     *
     * @param element The physics element to add.
     */
    public void addPhysicsElement(PhysicsElement element) {
        addPhysicsElement(element, true);
    }

    public void addPhysicsElement(PhysicsElement element, boolean isScheduled) {
        if (isScheduled) {
            schedulePhysicsTask(() -> {
                handleCollisionObjectAdding(element, element.getRigidBody());
            });
        } else {
            handleCollisionObjectAdding(element, element.getRigidBody());
        }
    }

    public void addCollisionObject(PhysicsCollisionObject pco) {
        addCollisionObject(pco, true);
    }

    public void addCollisionObject(PhysicsCollisionObject pco, boolean isScheduled) {
        if (isScheduled) {
            schedulePhysicsTask(() -> {
                handleCollisionObjectAdding(null, pco);
            });
        } else {
            handleCollisionObjectAdding(null, pco);
        }
    }

    private void handleCollisionObjectAdding(PhysicsElement element, PhysicsCollisionObject pco) {
        Validate.nonNull(pco, "collision object");

        if (!pco.isInWorld()) {
            if (element != null) {
                physicsElementMap.put(element.getRigidBody(), element);
                physicsElementCollection = physicsElementMap.values();
                pco.activate(true);
            }

            else if (pco instanceof BlockRigidBody terrain) {
                this.terrainMap.put(terrain.getBlockPos(), terrain);
            }

            super.addCollisionObject(pco);
        }
    }

    public void removePhysicsElement(PhysicsElement element) {
        removePhysicsElement(element, true);
    }

    public void removePhysicsElement(PhysicsElement element, boolean isScheduled) {
        if (isScheduled) {
            schedulePhysicsTask(() -> {
                handleCollisionObjectRemoval(element.getRigidBody());
            });
        } else {
            handleCollisionObjectRemoval(element.getRigidBody());
        }
    }

    public void removeCollisionObject(PhysicsCollisionObject collisionObject) {
        removeCollisionObject(collisionObject, true);
    }

    public void removeCollisionObject(PhysicsCollisionObject collisionObject, boolean isScheduled) {
        if (isScheduled) {
            schedulePhysicsTask(() -> {
                handleCollisionObjectRemoval(collisionObject);
            });
        } else {
            handleCollisionObjectRemoval(collisionObject);
        }
    }

    private void handleCollisionObjectRemoval(PhysicsCollisionObject collisionObject) {
        if (collisionObject.isInWorld()) {
            super.removeCollisionObject(collisionObject);
            PhysicsElement physicsElement = physicsElementMap.get(collisionObject);
            if (physicsElement == null) {
                if (collisionObject instanceof BlockRigidBody terrain) {
                    this.removeTerrainObjectAt(terrain.getBlockPos());
                }
            } else {
                physicsElementMap.remove(collisionObject);
                physicsElementCollection = physicsElementMap.values();
            }
        }
    }

    /**
     * Updates the block at the specified position.
     *
     * @param blockPos The block position.
     * @param blockState The block state.
     */
    public void doBlockUpdate(BlockPos blockPos, BlockState blockState) {
        this.generator.refresh(blockPos, blockState);
        this.wakeNearbyElementRigidBodies(blockPos);
    }

    /**
     * Activates nearby rigid bodies around the specified block position.
     *
     * @param blockPos The block position.
     */
    public void wakeNearbyElementRigidBodies(BlockPos blockPos) {
        for (PhysicsElement elementRigidBodyData : getPhysicsElements()) {
            if (elementRigidBodyData.isNear(blockPos)) {
                schedulePhysicsTask(() -> {
                    elementRigidBodyData.getRigidBody().activate();
                });
            }
        }
    }

    /**
     * Gets the terrain map.
     *
     * @return The terrain map.
     */
    public Map<BlockPos, BlockRigidBody> getTerrainMap() {
        return new HashMap<>(this.terrainMap);
    }

    /**
     * Gets the terrain object at the specified block position.
     *
     * @param blockPos The block position.
     * @return The terrain object, or null if none exists.
     */
    @Nullable
    public BlockRigidBody getTerrainObjectAt(BlockPos blockPos) {
        return terrainMap.get(blockPos);
    }

    /**
     * Removes the terrain object at the specified block position.
     *
     * @param blockPos The block position.
     */
    public void removeTerrainObjectAt(BlockPos blockPos) {
        final BlockRigidBody removed = terrainMap.remove(blockPos);

        if (removed != null) {
            this.removeCollisionObject(removed);
        }
    }

    /**
     * Sets the terrain generator based on the configuration.
     */
    public void setGenerator() {
        this.generator = new RayonGenerator(this);
    }


    /**
     * Gets all physics elements in the space.
     *
     * @return A collection of all physics elements.
     */
    public Collection<PhysicsElement> getPhysicsElements() {
        return physicsElementCollection;
    }

    /**
     * Gets the simulation thread pool.
     *
     * @return The simulation thread pool.
     */
    public SimulationThreadPool getSimulationThreadPool() {
        return simulationThreadPool;
    }

    /**
     * Gets the physics thread.
     *
     * @return The physics thread.
     */
    public PhysicsThread getThread() {
        return physicsThread;
    }

    /**
     * Gets the terrain generator.
     *
     * @return The terrain generator.
     */
    public ITerrainGenerator getGenerator() {
        return generator;
    }

    public void stepGenerator() {
        generator.step();
    }

    /**
     * Gets the Bukkit world associated with this space.
     *
     * @return The Bukkit world.
     */
    public World getWorld() {
        return world;
    }

    /**
     * Gets the debug bar.
     *
     * @return The debug bar.
     */
    public BossBar getDebugBar() {
        return debugBar;
    }

    /**
     * Gets the debug bar placeholder text.
     *
     * @return The debug bar placeholder text.
     */
    public String getDebugBarPlaceholder() {
        return debugBarPlaceholder;
    }

    /**
     * Updates the debug bar with the specified physics statistics.
     *
     * @param bodies The number of collision bodies.
     * @param vehicles The number of vehicles.
     * @param physMSPT The physics milliseconds per tick.
     * @param simMSPT The simulation milliseconds per tick.
     */
    private void updateDebug(int bodies, int vehicles, float physMSPT, float simMSPT) {
        this.debugBar.name(
                Component.text(
                        debugBarPlaceholder.formatted(worldName, bodies, vehicles, physMSPT, simMSPT)
                )
        );
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {}

    @Override
    public void onContactStarted(long manifoldId) {}

    @Override
    public void onContactProcessed(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long contactPointId) {
        if (InertiaPlugin.getPConfig().EVENTS.collisionEnable) {
            if (pcoA instanceof PhysicsRigidBody pcoAR && pcoB instanceof PhysicsRigidBody pcoBR) {
                if (threshold(pcoAR.getLinearVelocity(new Vector3f()))) {
                    Bukkit.getScheduler().callSyncMethod(InertiaPlugin.getInstance(), () -> {
                        Bukkit.getPluginManager().callEvent(new PhysicsContactEvent(this, pcoAR, pcoBR, contactPointId));

                        return null;
                    });
                }
            }
        }
    }

    @Override
    public void onContactEnded(long manifoldId) {}

    /**
     * Checks if the space is active based on the terrain map.
     *
     * @return True if the space is active, false otherwise.
     */
    public boolean isActive() {
        return !this.terrainMap.isEmpty();
    }

    /**
     * Checks if the given velocity vector exceeds the collision threshold.
     *
     * @param vector The velocity vector.
     * @return True if the vector exceeds the threshold, false otherwise.
     */
    private boolean threshold(Vector3f vector) {
        Vector3f th = InertiaPlugin.getPConfig().EVENTS.collisionThreshold;
        return (vector.getX() > th.getX() || vector.getX() < -th.getX()) &&
                (vector.getY() > th.getY() || vector.getY() < -th.getY()) &&
                (vector.getZ() > th.getZ() || vector.getZ() < -th.getZ());
    }

    /**
     * Gets the simulation milliseconds per tick.
     *
     * @return The simulation milliseconds per tick.
     */
    public long getSimulationMSPT() {
        return simulationProcessor.getMSPT();
    }

    /**
     * Gets the physics milliseconds per tick.
     *
     * @return The physics milliseconds per tick.
     */
    public long getPhysicsMSPT() {
        return physicsProcessor.getMSPT();
    }

    public void shutdown() {
        super.destroy();
        this.physicsThread.shutdown();
    }
}