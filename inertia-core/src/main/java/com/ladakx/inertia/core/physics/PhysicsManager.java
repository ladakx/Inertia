package com.ladakx.inertia.core.physics;

import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorMalloc;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.nativelib.JoltNatives;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the entire lifecycle of the Jolt physics simulation.
 */
public class PhysicsManager implements PhysicsWorld {

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    private TempAllocator tempAllocator;
    private JobSystem jobSystem;
    private PhysicsSystem physicsSystem;

    private BroadPhaseLayerInterfaceTable broadPhaseLayerInterface;
    private ObjectVsBroadPhaseLayerFilterTable objectVsBroadPhaseLayerFilter;
    private ObjectLayerPairFilterTable objectLayerPairFilter;

    private Thread physicsThread;
    private volatile boolean running = false;

    public static final int LAYER_NON_MOVING = 0;
    public static final int LAYER_MOVING = 1;

    public PhysicsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        if (!JoltNatives.load(plugin)) {
            return false;
        }

        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();

        if (!Jolt.newFactory()) {
            InertiaPluginLogger.severe("Failed to create Jolt factory.");
            return false;
        }
        Jolt.registerTypes();

        this.tempAllocator = new TempAllocatorMalloc();

        int maxJobs = Jolt.cMaxPhysicsJobs;
        int maxBarriers = Jolt.cMaxPhysicsBarriers;
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.jobSystem = new JobSystemThreadPool(maxJobs, maxBarriers, numThreads);

        createCollisionFilters();

        this.physicsSystem = new PhysicsSystem();
        int maxBodies = 10240;
        int numBodyMutexes = 0;
        int maxBodyPairs = 65536;
        int maxContactConstraints = 10240;

        physicsSystem.init(
                maxBodies,
                numBodyMutexes,
                maxBodyPairs,
                maxContactConstraints,
                broadPhaseLayerInterface,
                objectVsBroadPhaseLayerFilter,
                objectLayerPairFilter
        );

        this.running = true;
        this.physicsThread = new Thread(this::physicsLoop, "Inertia-Physics-Thread");
        this.physicsThread.start();

        InertiaPluginLogger.info("PhysicsManager initialized and simulation thread started.");
        return true;
    }

    private void createCollisionFilters() {
        final int numObjectLayers = 2;
        final int numBroadPhaseLayers = 2;

        this.broadPhaseLayerInterface = new BroadPhaseLayerInterfaceTable(numObjectLayers, numBroadPhaseLayers);
        broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(LAYER_NON_MOVING, 0);
        broadPhaseLayerInterface.mapObjectToBroadPhaseLayer(LAYER_MOVING, 1);

        this.objectLayerPairFilter = new ObjectLayerPairFilterTable(numObjectLayers);
        objectLayerPairFilter.enableCollision(LAYER_NON_MOVING, LAYER_MOVING);
        objectLayerPairFilter.enableCollision(LAYER_MOVING, LAYER_MOVING);

        this.objectVsBroadPhaseLayerFilter = new ObjectVsBroadPhaseLayerFilterTable(
                this.broadPhaseLayerInterface, numBroadPhaseLayers,
                this.objectLayerPairFilter, numObjectLayers
        );
    }

    private void physicsLoop() {
        final double tickRate = 60.0;
        final float deltaTime = (float) (1.0 / tickRate);
        final long tickTimeNanos = (long) (1_000_000_000L / tickRate);

        long lastTickTime = System.nanoTime();

        while (running) {
            processCommandQueue();

            int collisionSteps = 1;
            physicsSystem.update(deltaTime, collisionSteps, tempAllocator, jobSystem);

            long currentTime = System.nanoTime();
            long elapsedTime = currentTime - lastTickTime;
            long sleepTime = (tickTimeNanos - elapsedTime) / 1_000_000L;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            lastTickTime = System.nanoTime();
        }
        InertiaPluginLogger.info("Physics loop has stopped.");
    }

    private void processCommandQueue() {
        Runnable task;
        while ((task = commandQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                InertiaPluginLogger.severe("Error executing task in physics thread: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        if (!running) {
            return;
        }

        InertiaPluginLogger.info("Shutting down PhysicsManager...");
        running = false;

        try {
            if (physicsThread != null) {
                physicsThread.join(1000);
            }
        } catch (InterruptedException e) {
            InertiaPluginLogger.warning("Interrupted while waiting for physics thread to shut down.");
            Thread.currentThread().interrupt();
        }

        if (physicsSystem != null) {
            physicsSystem.close();
        }
        if (jobSystem != null) {
            jobSystem.close();
        }
        if (tempAllocator != null) {
            tempAllocator.close();
        }

        InertiaPluginLogger.info("PhysicsManager shut down and resources released.");
    }
}

