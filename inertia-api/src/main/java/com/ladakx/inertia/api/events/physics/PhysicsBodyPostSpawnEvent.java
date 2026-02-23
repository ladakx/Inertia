package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Sync event emitted after a physics body has been spawned.
 * Payload contains the spawned body and versioned metadata.
 * Bukkit API calls are allowed as for main-thread sync events.
 */
public class PhysicsBodyPostSpawnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBodyLifecyclePayload payload;

    public PhysicsBodyPostSpawnEvent(@NotNull PhysicsBody body) {
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
