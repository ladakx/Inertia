package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportInputAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TransportInputPayload payload;

    public TransportInputAppliedEvent(@NotNull TransportInputPayload payload) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull TransportInputPayload getPayload() {
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
