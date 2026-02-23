package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public class PhysicsWorldTickEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsWorldTickPayload payload;

    public PhysicsWorldTickEndEvent(@NotNull PhysicsWorldTickPayload payload) {
        super(true);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    @ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
    public @NotNull PhysicsWorldTickPayload getPayload() {
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
