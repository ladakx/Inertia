package com.ladakx.inertia.core.physics;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorMalloc;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.body.InertiaBodyImpl;
import com.ladakx.inertia.core.nativelib.JoltNatives;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the entire lifecycle of the Jolt physics simulation.
 * This class is the heart of the physics engine integration, responsible for:
 * - Initializing and shutting down Jolt.
 * - Running the simulation loop in a dedicated thread.
 * - Managing physics bodies.
 * - Safely synchronizing data between the physics thread and the main server thread.
 */
public class PhysicsManager implements PhysicsWorld {

    // Record to hold the state of a body for thread-safe data transfer.
    public record BodyState(int bodyId, Vector position, Quaternionf rotation) {}

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private final Map<Integer, InertiaBodyImpl> bodies = new ConcurrentHashMap<>();

    // Double buffering mechanism for simulation results
    private final AtomicReference<Map<Integer, BodyState>> resultsBuffer = new AtomicReference<>(new ConcurrentHashMap<>());
    private Map<Integer, BodyState> writeBuffer = new ConcurrentHashMap<>();

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

            prepareResults();
            swapBuffers();

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

    private void prepareResults() {
        writeBuffer.clear();
        for (InertiaBodyImpl body : bodies.values()) {
            if (body.getJoltBody().isActive()) {
                RVec3 pos = body.getJoltBody().getPosition();
                Quat rot = body.getJoltBody().getRotation();

                BodyState state = new BodyState(
                        body.getId(),
                        new Vector((double) pos.getX(), (double) pos.getY(), (double) pos.getZ()),
                        new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW())
                );
                writeBuffer.put(body.getId(), state);
            }
        }
    }

    private void swapBuffers() {
        Map<Integer, BodyState> oldWriteBuffer = this.resultsBuffer.getAndSet(this.writeBuffer);
        this.writeBuffer = oldWriteBuffer;
    }

    /**
     * Queues a command to be executed on the physics thread.
     * @param command The command to execute.
     */
    public void queueCommand(Runnable command) {
        commandQueue.add(command);
    }

    /**
     * Adds a managed body to the PhysicsManager.
     * @param body The body to add.
     */
    public void addBody(InertiaBodyImpl body) {
        bodies.put(body.getId(), body);
    }

    /**
     * Gets the Jolt BodyInterface for direct manipulation (intended for internal use).
     * @return The BodyInterface.
     */
    public BodyInterface getBodyInterface() {
        return physicsSystem.getBodyInterface();
    }

    /**
     * Gets the latest snapshot of all body states from the physics simulation.
     * This is called by the main thread's synchronization task.
     *
     * @return A map of body IDs to their current {@link BodyState}.
     */
    public Map<Integer, BodyState> getLatestBodyStates() {
        return resultsBuffer.get();
    }

    /**
     * Retrieves a map of all managed physics bodies.
     *
     * @return A map of body IDs to their corresponding {@link InertiaBodyImpl} instances.
     */
    public Map<Integer, InertiaBodyImpl> getBodies() {
        return bodies;
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

