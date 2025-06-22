/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/engine/PhysicsEngine.java */

package com.ladakx.inertia.core.engine;

import com.ladakx.inertia.core.ntve.JNIBridge;
import com.ladakx.inertia.core.ntve.NativeLoader;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public final class PhysicsEngine implements Runnable {

    // --- Constants ---
    private static final int TICK_RATE = 60;
    private static final float TICK_DELTA_TIME = 1.0f / TICK_RATE;
    private static final long TICK_INTERVAL_MS = 1000 / TICK_RATE;
    private static final int MAX_BODIES = 10240;
    // Each body transform takes 8 (long) + 7 * 4 (float) = 36 bytes
    private static final int TRANSFORM_BUFFER_SIZE = MAX_BODIES * 36;

    // --- State ---
    private volatile boolean running = false;
    private Thread physicsThread;

    // Command queue for thread-safe operations from main thread to physics thread
    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    // Double-buffering system for transforms
    private final AtomicReference<Map<Long, PhysicsTransform>> activeTransforms = new AtomicReference<>(new ConcurrentHashMap<>());

    // Buffer for JNI communication
    private final ByteBuffer transformBuffer = ByteBuffer.allocateDirect(TRANSFORM_BUFFER_SIZE).order(ByteOrder.nativeOrder());

    public void start() {
        if (running) {
            throw new IllegalStateException("Physics engine is already running.");
        }
        this.running = true;
        this.physicsThread = new Thread(this, "Inertia-Physics-Thread");
        this.physicsThread.start();
    }

    public void stop() {
        if (!running) return;
        this.running = false;
        commandQueue.add(JNIBridge::shutdown); // Ensure shutdown happens on the physics thread
        try {
            this.physicsThread.join(5000); // Wait up to 5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to cleanly stop the physics thread.");
        }
    }

    /**
     * Schedules a command to be executed safely on the physics thread.
     * @param command The command to execute.
     */
    public void scheduleCommand(Runnable command) {
        commandQueue.add(command);
    }

    /**
     * Gets the latest completed physics transform for a given body.
     * This is safe to call from the main Minecraft thread.
     * @param bodyId The ID of the body.
     * @return The transform, or null if not found.
     */
    public PhysicsTransform getTransform(long bodyId) {
        return activeTransforms.get().get(bodyId);
    }

    @Override
    public void run() {
        NativeLoader.load();
        JNIBridge.init(MAX_BODIES, 0);
        System.out.println("Inertia physics thread started.");

        long nextTickTime = System.currentTimeMillis();

        while (running) {
            // Process all pending commands from the main thread
            Runnable command;
            while ((command = commandQueue.poll()) != null) {
                command.run();
            }

            // If the running flag was set to false by a command, exit
            if (!running) break;

            // Update physics and get results
            transformBuffer.clear();
            int bodyCount = JNIBridge.updateAndGetTransforms(TICK_DELTA_TIME, transformBuffer);

            // Prepare the back buffer with new data
            Map<Long, PhysicsTransform> backBuffer = new HashMap<>();
            for (int i = 0; i < bodyCount; i++) {
                long bodyId = transformBuffer.getLong();
                float posX = transformBuffer.getFloat();
                float posY = transformBuffer.getFloat();
                float posZ = transformBuffer.getFloat();
                float rotX = transformBuffer.getFloat();
                float rotY = transformBuffer.getFloat();
                float rotZ = transformBuffer.getFloat();
                float rotW = transformBuffer.getFloat();
                backBuffer.put(bodyId, new PhysicsTransform(new Vector3f(posX, posY, posZ), new Quaternionf(rotX, rotY, rotZ, rotW)));
            }

            // Atomically swap the back buffer to the front
            activeTransforms.set(backBuffer);

            // Wait for the next tick
            nextTickTime += TICK_INTERVAL_MS;
            long sleepTime = nextTickTime - System.currentTimeMillis();
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    this.running = false;
                }
            }
        }
        System.out.println("Inertia physics thread stopped.");
    }

    /**
     * A simple data class to hold position and rotation.
     * This is immutable to ensure thread safety.
     */
    public record PhysicsTransform(Vector3f position, Quaternionf rotation) {}
}