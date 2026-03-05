package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.transport.TransportHandle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportPostSpawnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TransportLifecyclePayload payload;
    private final TransportHandle handle;

    public TransportPostSpawnEvent(@NotNull TransportLifecyclePayload payload,
                                   @NotNull TransportHandle handle) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public @NotNull TransportLifecyclePayload getPayload() {
        return payload;
    }

    public @NotNull TransportHandle getHandle() {
        return handle;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
