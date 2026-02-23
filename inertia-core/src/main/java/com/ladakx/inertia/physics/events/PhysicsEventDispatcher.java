package com.ladakx.inertia.physics.events;

import com.ladakx.inertia.api.events.ImmutablePhysicsEventPayload;
import com.ladakx.inertia.api.events.PhysicsEventPayload;
import org.bukkit.event.Event;

import java.util.Objects;

public final class PhysicsEventDispatcher {
    private final MainThreadExecutor mainThreadExecutor;
    private final EventPublisher eventPublisher;

    public PhysicsEventDispatcher(MainThreadExecutor mainThreadExecutor, EventPublisher eventPublisher) {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public void dispatchSync(Event event) {
        Objects.requireNonNull(event, "event");
        mainThreadExecutor.executeBlocking(() -> eventPublisher.publish(event));
    }

    public void dispatchAsync(Event event, PhysicsEventPayload payload) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(payload, "payload");
        if (!(payload instanceof ImmutablePhysicsEventPayload)) {
            throw new IllegalArgumentException("async-event-payload-must-be-immutable");
        }
        eventPublisher.publish(event);
    }

    public interface MainThreadExecutor {
        void executeBlocking(Runnable runnable);
    }

    public interface EventPublisher {
        void publish(Event event);
    }
}
