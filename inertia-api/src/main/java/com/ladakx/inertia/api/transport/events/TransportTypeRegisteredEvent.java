package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportTypeRegisteredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TransportTypeRegisteredPayload payload;

    public TransportTypeRegisteredEvent(@NotNull TransportTypeRegisteredPayload payload) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull TransportTypeRegisteredPayload payload() {
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

