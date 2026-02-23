package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Sync cancellable event emitted before a physics body destroy operation.
 * Cancellation is applied only when isCancellationSupported() is true.
 * Bukkit API calls are allowed as for main-thread sync events.
 */
public class PhysicsBodyPreDestroyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBodyLifecyclePayload payload;
    private final boolean cancellationSupported;
    private boolean cancelled;

    public PhysicsBodyPreDestroyEvent(@NotNull PhysicsBody body, boolean cancellationSupported) {
        super(false);
        Objects.requireNonNull(body, "body");
        this.cancellationSupported = cancellationSupported;
        this.payload = new PhysicsBodyLifecyclePayload(
                PhysicsEventPayload.SCHEMA_VERSION_V1,
                Objects.requireNonNull(body.getLocation().getWorld(), "body.location.world").getUID(),
                body.getBodyId(),
                body
        );
    }

    public @NotNull PhysicsBody getBody() {
        return payload.body();
    }

    public boolean isCancellationSupported() {
        return cancellationSupported;
    }

    public @NotNull PhysicsBodyLifecyclePayload getPayload() {
        return payload;
    }

    @Override
    public boolean isCancelled() {
        return cancellationSupported && cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        if (cancellationSupported) {
            this.cancelled = cancel;
        }
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
