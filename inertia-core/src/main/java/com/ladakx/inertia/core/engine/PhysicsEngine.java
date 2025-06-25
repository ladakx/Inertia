/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/engine/PhysicsEngine.java */

package com.ladakx.inertia.core.engine;

import com.ladakx.inertia.core.ntve.JNIBridge;
import com.ladakx.inertia.core.ntve.NativeLibraryManager;
import org.bukkit.plugin.Plugin;
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

    private final Plugin plugin; // Додаємо посилання на плагін

    // ... (решта констант та полів залишається без змін)
    private static final int TICK_RATE = 60;
    private static final float TICK_DELTA_TIME = 1.0f / TICK_RATE;
    private static final long TICK_INTERVAL_MS = 1000 / TICK_RATE;
    private static final int MAX_BODIES = 10240;
    private static final int TRANSFORM_BUFFER_SIZE = MAX_BODIES * 36;
    private volatile boolean running = false;
    private Thread physicsThread;
    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Map<Long, PhysicsTransform>> activeTransforms = new AtomicReference<>(new ConcurrentHashMap<>());
    private final ByteBuffer transformBuffer = ByteBuffer.allocateDirect(TRANSFORM_BUFFER_SIZE).order(ByteOrder.nativeOrder());


    public PhysicsEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            throw new IllegalStateException("Physics engine is already running.");
        }
        this.running = true;
        this.physicsThread = new Thread(this, "Inertia-Physics-Thread");
        this.physicsThread.start();
    }

    // ... (решта методів: stop, scheduleCommand, getTransform - залишаються без змін)
    public void stop() {
        if (!running) return;
        this.running = false;
        commandQueue.add(JNIBridge::shutdown);
        try {
            this.physicsThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to cleanly stop the physics thread.");
        }
    }
    public void scheduleCommand(Runnable command) {
        commandQueue.add(command);
    }
    public PhysicsTransform getTransform(long bodyId) {
        return activeTransforms.get().get(bodyId);
    }

    @Override
    public void run() {
        // --- ОСНОВНЕ ВИПРАВЛЕННЯ ---
        // Використовуємо наш новий завантажувач
        new NativeLibraryManager(plugin).load();

        JNIBridge.init(MAX_BODIES, 0);
        plugin.getLogger().info("Inertia physics thread started.");

        long nextTickTime = System.currentTimeMillis();

        while (running) {
            // ... (решта циклу залишається без змін) ...
            Runnable command;
            while ((command = commandQueue.poll()) != null) {
                command.run();
            }
            if (!running) break;
            transformBuffer.clear();
            int bodyCount = JNIBridge.updateAndGetTransforms(TICK_DELTA_TIME, transformBuffer);
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
            activeTransforms.set(backBuffer);
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
        plugin.getLogger().info("Inertia physics thread stopped.");
    }

    public record PhysicsTransform(Vector3f position, Quaternionf rotation) {}
}