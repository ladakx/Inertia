package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public final class PhysicsBodyInteractEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final PhysicsBodyInteractPayload payload;
    private boolean cancelled;

    public PhysicsBodyInteractEvent(@NotNull PhysicsBodyInteractPayload payload) {
        super(false);
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public @NotNull PhysicsBodyInteractPayload getPayload() {
        return payload;
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
