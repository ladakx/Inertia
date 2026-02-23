package com.ladakx.inertia.api.events.physics;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
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

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public @NotNull PhysicsBody getBody() {
        return payload.body();
    }

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public boolean isCancellationSupported() {
        return cancellationSupported;
    }

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public @NotNull PhysicsBodyLifecyclePayload getPayload() {
        return payload;
    }

    @Override
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public boolean isCancelled() {
        return cancellationSupported && cancelled;
    }

    @Override
    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    public void setCancelled(boolean cancel) {
        if (cancellationSupported) {
            this.cancelled = cancel;
        }
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
