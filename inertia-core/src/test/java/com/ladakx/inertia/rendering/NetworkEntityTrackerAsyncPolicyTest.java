package com.ladakx.inertia.rendering;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkEntityTrackerAsyncPolicyTest {

    @Test
    void shouldKeepOnlyLatestQueuedAsyncRequestWhenFutureIsNotReady() throws Exception {
        NetworkEntityTracker tracker = new NetworkEntityTracker(new NoopPacketFactory());
        try {
            CompletableFuture<Void> blockedFuture = new CompletableFuture<>();
            setField(tracker, "asyncPhaseFuture", blockedFuture);

            tracker.tick(Collections.emptyList(), 256.0);
            tracker.tick(Collections.emptyList(), 1024.0);

            assertEquals(2L, tracker.getAsyncSkippedTicks());
            assertEquals(1L, tracker.getAsyncFallbackUsed());

            Object queuedRequest = getField(tracker, "queuedAsyncPhaseRequest");
            assertNotNull(queuedRequest);
            Method viewDistanceMethod = queuedRequest.getClass().getDeclaredMethod("viewDistanceSquared");
            viewDistanceMethod.setAccessible(true);
            double latestDistance = (double) viewDistanceMethod.invoke(queuedRequest);
            assertEquals(1024.0, latestDistance, 0.0001);
        } finally {
            tracker.clear();
        }
    }

    @Test
    void shouldScaleBacklogMultiplierByDurationInsteadOfSimpleFutureCounter() throws Exception {
        NetworkEntityTracker tracker = new NetworkEntityTracker(new NoopPacketFactory());
        try {
            Method resolveMethod = NetworkEntityTracker.class.getDeclaredMethod("resolveBacklogPressureMultiplier", long.class);
            resolveMethod.setAccessible(true);

            int fast = (int) resolveMethod.invoke(tracker, 20_000_000L);
            int occasionalOverrun = (int) resolveMethod.invoke(tracker, 55_000_000L);
            int heavy = (int) resolveMethod.invoke(tracker, 95_000_000L);

            assertEquals(1, fast);
            assertEquals(2, occasionalOverrun);
            assertEquals(4, heavy);
        } finally {
            tracker.clear();
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class NoopPacketFactory implements PacketFactory {
        @Override
        public Object createSpawnPacket(com.ladakx.inertia.rendering.config.RenderEntityDefinition.EntityKind kind, int entityId, UUID uuid, double x, double y, double z, float yaw, float pitch) {
            return new Object();
        }

        @Override
        public Object createMetaPacket(int entityId, List<?> dataValues) {
            return new Object();
        }

        @Override
        public Object createTeleportPacket(int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround) {
            return new Object();
        }

        @Override
        public Object createDestroyPacket(int... ids) {
            return new Object();
        }

        @Override
        public Object createBundlePacket(List<Object> packets) {
            return new Object();
        }

        @Override
        public void sendPacket(org.bukkit.entity.Player player, Object packet) {
        }

        @Override
        public void sendBundle(org.bukkit.entity.Player player, List<Object> packets) {
        }
    }
}
