package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public class PhysicsWorldOverloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsWorldOverloadPayload payload;

    public PhysicsWorldOverloadEvent(@NotNull PhysicsWorldOverloadPayload payload) {
        super(true);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull PhysicsWorldOverloadPayload getPayload() {
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
