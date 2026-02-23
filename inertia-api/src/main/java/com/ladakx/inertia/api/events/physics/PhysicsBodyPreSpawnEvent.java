package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Sync cancellable event emitted before a physics body spawn is finalized.
 * Payload body reference is mutable only through allowed body operations.
 * Bukkit API calls are allowed as for main-thread sync events.
 */
public class PhysicsBodyPreSpawnEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBodyLifecyclePayload payload;
    private boolean cancelled;

    public PhysicsBodyPreSpawnEvent(@NotNull PhysicsBody body) {
        super(false);
        Objects.requireNonNull(body, "body");
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

    public @NotNull PhysicsBodyLifecyclePayload getPayload() {
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
