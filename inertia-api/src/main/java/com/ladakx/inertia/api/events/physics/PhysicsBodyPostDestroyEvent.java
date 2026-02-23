package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
public class PhysicsBodyPostDestroyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final PhysicsBodyLifecyclePayload payload;

    public PhysicsBodyPostDestroyEvent(@NotNull PhysicsBody body) {
        super(false);
        Objects.requireNonNull(body, "body");
        this.payload = new PhysicsBodyLifecyclePayload(
                PhysicsEventPayload.SCHEMA_VERSION_V1,
                Objects.requireNonNull(body.getLocation().getWorld(), "body.location.world").getUID(),
                body.getBodyId(),
                body
        );
    }

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public @NotNull PhysicsBody getBody() {
        return payload.body();
    }

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public @NotNull PhysicsBodyLifecyclePayload getPayload() {
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
