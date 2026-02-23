package com.ladakx.inertia.api.events.physics;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Sync event emitted when physics-world pause state changes.
 * Payload is read-only and versioned.
 * Bukkit API calls are allowed as for main-thread sync events.
 */
public class PhysicsWorldPauseChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsWorldPauseChangedPayload payload;

    public PhysicsWorldPauseChangedEvent(@NotNull PhysicsWorldPauseChangedPayload payload) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull PhysicsWorldPauseChangedPayload getPayload() {
        return payload;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
