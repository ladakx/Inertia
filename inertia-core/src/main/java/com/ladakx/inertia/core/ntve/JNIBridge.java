/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/ntve/JNIBridge.java */

package com.ladakx.inertia.core.ntve;

import java.nio.ByteBuffer;

public final class JNIBridge {

    // --- Lifecycle ---
    /**
     * Initializes the native physics system.
     * @param maxBodies The maximum number of bodies the simulation can handle.
     * @param numThreads The number of threads to use for physics processing (0 for default).
     */
    public static native void init(int maxBodies, int numThreads);

    /**
     * Shuts down the native physics system and cleans up all resources.
     */
    public static native void shutdown();

    // --- Simulation & Data Transfer ---
    /**
     * Performs a single physics simulation step and writes the transform data
     * of all active bodies into the provided direct ByteBuffer.
     * The buffer format for each body is: [long bodyId, float posX, float posY, float posZ, float rotX, float rotY, float rotZ, float rotW]
     *
     * @param deltaTime The time elapsed since the last update.
     * @param byteBuffer A direct-allocated ByteBuffer to write the results into.
     * @return The number of active bodies written to the buffer.
     */
    public static native int updateAndGetTransforms(float deltaTime, ByteBuffer byteBuffer);

    // --- Body Management ---
    /**
     * Creates a new box-shaped rigid body in the physics world.
     * @return The unique stable ID of the newly created body. Returns -1 on failure.
     */
    public static native long createBoxBody(
            double posX, double posY, double posZ,
            double rotX, double rotY, double rotZ, double rotW,
            int bodyType,
            float halfExtentX, float halfExtentY, float halfExtentZ
    );

    /**
     * Destroys a body and removes it from the simulation.
     * @param bodyId The stable ID of the body to destroy.
     */
    public static native void destroyBody(long bodyId);
}