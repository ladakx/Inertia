package com.ladakx.inertia.physics.events;

import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class BukkitMainThreadExecutor implements PhysicsEventDispatcher.MainThreadExecutor {
    private final Plugin plugin;

    public BukkitMainThreadExecutor(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void executeBlocking(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                runnable.run();
                return Boolean.TRUE;
            }).get();
        } catch (Exception exception) {
            InertiaLogger.error("physics-sync-dispatch-failed", exception);
        }
    }
}
