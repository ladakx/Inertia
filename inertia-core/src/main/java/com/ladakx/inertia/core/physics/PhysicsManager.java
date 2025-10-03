package com.ladakx.inertia.core.physics;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.nativelib.JoltNatives;

import org.bukkit.plugin.java.JavaPlugin;

public class PhysicsManager {

    private final JavaPlugin plugin;

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

        return true;
    }

    public void shutdown() {
        InertiaPluginLogger.info("Shutting down PhysicsManager...");
        InertiaPluginLogger.info("PhysicsManager shut down and resources released.");
    }
}

