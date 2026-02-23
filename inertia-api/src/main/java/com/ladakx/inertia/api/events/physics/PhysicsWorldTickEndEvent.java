package com.ladakx.inertia.api.events.physics;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Async physics-thread event emitted at world tick end.
 * Payload is read-only, immutable, and versioned.
 * Bukkit API calls are limited to thread-safe operations only.
 */
public class PhysicsWorldTickEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsWorldTickPayload payload;

    public PhysicsWorldTickEndEvent(@NotNull PhysicsWorldTickPayload payload) {
        super(true);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull PhysicsWorldTickPayload getPayload() {
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
