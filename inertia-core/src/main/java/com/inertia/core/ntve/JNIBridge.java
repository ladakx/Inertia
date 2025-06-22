package com.inertia.core.ntve;

public final class JNIBridge {
    public static native void init(int maxBodies, int numBodyMutexes, int maxBodyPairs, int maxContactConstraints);
    public static native void update(float deltaTime);
    public static native void shutdown();
    public static native String getJoltVersion();
}