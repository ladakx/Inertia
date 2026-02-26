package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportDestroyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TransportDestroyPayload payload;

    public TransportDestroyEvent(@NotNull TransportDestroyPayload payload) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull TransportDestroyPayload payload() {
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

