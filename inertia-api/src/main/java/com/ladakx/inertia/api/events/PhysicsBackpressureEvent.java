package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public class PhysicsBackpressureEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBackpressurePayload payload;

    public PhysicsBackpressureEvent(@NotNull PhysicsBackpressurePayload payload) {
        super(true);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull PhysicsBackpressurePayload getPayload() {
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
