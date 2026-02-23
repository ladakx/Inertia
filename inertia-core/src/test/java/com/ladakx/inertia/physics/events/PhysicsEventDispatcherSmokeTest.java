package com.ladakx.inertia.physics.events;

import com.ladakx.inertia.api.events.physics.ImmutablePhysicsEventPayload;
import com.ladakx.inertia.api.events.physics.PhysicsEventPayload;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class PhysicsEventDispatcherSmokeTest {
    @Test
    void destroySpawnDuringTickDispatchesSyncOnMainThreadExecutor() {
        AtomicInteger mainThreadExecutions = new AtomicInteger();
        List<String> calls = new CopyOnWriteArrayList<>();
        PhysicsEventDispatcher dispatcher = new PhysicsEventDispatcher(
                runnable -> {
                    mainThreadExecutions.incrementAndGet();
                    runnable.run();
                },
                event -> calls.add(event.getEventName())
        );

        dispatcher.dispatchSync(new TestEvent());
        dispatcher.dispatchSync(new TestEvent());

        Assertions.assertEquals(2, mainThreadExecutions.get());
        Assertions.assertEquals(2, calls.size());
    }

    @Test
    void listenerLatencyDoesNotDropAsyncEvent() {
        List<String> calls = new CopyOnWriteArrayList<>();
        PhysicsEventDispatcher dispatcher = new PhysicsEventDispatcher(
                Runnable::run,
                event -> {
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    calls.add(event.getEventName());
                }
        );

        dispatcher.dispatchAsync(new TestEvent(), new ImmutablePayload());

        Assertions.assertEquals(List.of("TestEvent"), calls);
    }

    @Test
    void eventReentrancyIsSupportedForSyncDispatch() {
        AtomicInteger invocations = new AtomicInteger();
        final PhysicsEventDispatcher[] holder = new PhysicsEventDispatcher[1];
        holder[0] = new PhysicsEventDispatcher(
                Runnable::run,
                event -> {
                    int value = invocations.incrementAndGet();
                    if (value == 1) {
                        holder[0].dispatchSync(new TestEvent());
                    }
                }
        );

        holder[0].dispatchSync(new TestEvent());

        Assertions.assertEquals(2, invocations.get());
    }

    @Test
    void mutablePayloadIsRejectedForAsyncDispatch() {
        PhysicsEventDispatcher dispatcher = new PhysicsEventDispatcher(Runnable::run, event -> {});

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatchAsync(new TestEvent(), new MutablePayload()));
    }

    private static final class TestEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        private TestEvent() {
            super(true);
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    private record ImmutablePayload() implements ImmutablePhysicsEventPayload {
        @Override
        public int schemaVersion() {
            return PhysicsEventPayload.SCHEMA_VERSION_V1;
        }
    }

    private static final class MutablePayload implements PhysicsEventPayload {
        private int value;

        @Override
        public int schemaVersion() {
            return Objects.hash(value);
        }
    }
}
