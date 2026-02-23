package com.ladakx.inertia.physics.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.Objects;

public final class BukkitEventPublisher implements PhysicsEventDispatcher.EventPublisher {
    @Override
    public void publish(Event event) {
        Bukkit.getPluginManager().callEvent(Objects.requireNonNull(event, "event"));
    }
}
