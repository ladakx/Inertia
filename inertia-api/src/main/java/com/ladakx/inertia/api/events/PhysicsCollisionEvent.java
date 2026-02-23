package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Async event emitted on the physics thread when a collision contact is generated.
 * Uses immutable payload only and must be handled as read-only.
 */
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public class PhysicsCollisionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsCollisionPayload payload;

    public PhysicsCollisionEvent(@NotNull PhysicsCollisionPayload payload) {
        super(true);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    public @NotNull String getBodyAId() {
        return payload.bodyAId();
    }

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    public @NotNull String getBodyBId() {
        return payload.bodyBId();
    }

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    public @NotNull Vector getContactPoint() {
        return new Vector(payload.contactPointX(), payload.contactPointY(), payload.contactPointZ());
    }

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    public @NotNull PhysicsCollisionPayload getPayload() {
        return payload;
    }

    @Override
    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
