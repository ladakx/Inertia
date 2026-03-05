package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.transport.TransportHandle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class TransportPreDestroyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final TransportLifecyclePayload payload;
    private final TransportHandle handle;
    private boolean cancelled;

    public TransportPreDestroyEvent(@NotNull TransportLifecyclePayload payload,
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
