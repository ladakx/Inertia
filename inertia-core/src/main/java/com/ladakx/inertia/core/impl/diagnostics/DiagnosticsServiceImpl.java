package com.ladakx.inertia.core.impl.diagnostics;

import com.ladakx.inertia.api.diagnostics.BodyCounters;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsSlaContract;
import com.ladakx.inertia.api.diagnostics.MetricsServiceSnapshot;
import com.ladakx.inertia.api.diagnostics.QueueBackpressureCounters;
import com.ladakx.inertia.api.diagnostics.TickDurationPercentiles;
import com.ladakx.inertia.api.diagnostics.TransportDiagnosticsSnapshot;
import com.ladakx.inertia.api.diagnostics.TransportWorldSnapshot;
import com.ladakx.inertia.api.diagnostics.WorldHealthSnapshot;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.transport.TransportHandle;
import com.ladakx.inertia.api.transport.TransportService;
import com.ladakx.inertia.api.transport.TransportState;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.physics.world.loop.LoopDiagnosticsSnapshot;
import com.ladakx.inertia.physics.world.loop.LoopTickListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class DiagnosticsServiceImpl implements DiagnosticsService, LoopTickListener {
    private static final int WINDOW_TICKS = 256;
    private static final DiagnosticsSlaContract SLA_CONTRACT = new DiagnosticsSlaContract(20, 150, "physics-loop-thread", WINDOW_TICKS);

    private final PhysicsMetricsService metricsService;
    private final Map<String, WorldAccumulator> worldAccumulators = new ConcurrentHashMap<>();

    public DiagnosticsServiceImpl(PhysicsMetricsService metricsService) {
        this.metricsService = Objects.requireNonNull(metricsService, "metricsService");
    }

    @Override
    public @NotNull Collection<WorldHealthSnapshot> getWorldHealthSnapshots() {
        Collection<WorldAccumulator> accumulators = new ArrayList<>(worldAccumulators.values());
        Collection<WorldHealthSnapshot> snapshots = new ArrayList<>(accumulators.size());
        for (WorldAccumulator accumulator : accumulators) {
            snapshots.add(accumulator.snapshot(metricsService.getReadOnlySnapshot()));
        }
        return snapshots;
    }

    @Override
    public @NotNull WorldHealthSnapshot getWorldHealthSnapshot(@NotNull String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        WorldAccumulator accumulator = worldAccumulators.get(worldName);
        if (accumulator == null) {
            return new WorldHealthSnapshot(
                    new UUID(0L, 0L),
                    worldName,
                    false,
                    new TickDurationPercentiles(0.0d, 0.0d, 0.0d, 0.0d, 0.0d),
                    new QueueBackpressureCounters(0, 0L, 0L, 0L, 0L, 0, 0),
                    new BodyCounters(0, 0, 0, 0),
                    0L,
                    0L
            );
        }
        return accumulator.snapshot(metricsService.getReadOnlySnapshot());
    }

    @Override
    public @NotNull DiagnosticsSlaContract getSlaContract() {
        return SLA_CONTRACT;
    }

    @Override
    public @NotNull TransportDiagnosticsSnapshot getTransportDiagnosticsSnapshot() {
        try {
            TransportService transportService = InertiaApiAccess.resolve().transport();
            Collection<TransportHandle> handles = transportService.getAll();
            if (handles.isEmpty()) {
                return new TransportDiagnosticsSnapshot(0, 0, 0, 0.0d, 0.0d, List.of(), System.nanoTime());
            }

            Map<UUID, TransportWorldAccumulator> byWorld = new ConcurrentHashMap<>();
            int total = 0;
            int active = 0;
            int grounded = 0;
            double totalSpeed = 0.0d;
            double maxSpeed = 0.0d;

            for (TransportHandle handle : handles) {
                TransportState state = transportService.getState(handle.id());
                if (state == null) {
                    continue;
                }
                total++;
                if (state.speedKmh() > 0.01d) {
                    active++;
                }
                if (state.grounded()) {
                    grounded++;
                }
                totalSpeed += state.speedKmh();
                maxSpeed = Math.max(maxSpeed, state.speedKmh());

                UUID worldId = Objects.requireNonNull(state.location().getWorld(), "transport world").getUID();
                String worldName = state.location().getWorld().getName();
                byWorld.computeIfAbsent(worldId, id -> new TransportWorldAccumulator(id, worldName)).accumulate(state);
            }

            List<TransportWorldSnapshot> worldSnapshots = new ArrayList<>(byWorld.size());
            for (TransportWorldAccumulator accumulator : byWorld.values()) {
                worldSnapshots.add(accumulator.snapshot());
            }
            worldSnapshots.sort((a, b) -> a.worldName().compareToIgnoreCase(b.worldName()));

            double averageSpeed = total == 0 ? 0.0d : totalSpeed / total;
            return new TransportDiagnosticsSnapshot(total, active, grounded, averageSpeed, maxSpeed, worldSnapshots, System.nanoTime());
        } catch (Exception ignored) {
            return new TransportDiagnosticsSnapshot(0, 0, 0, 0.0d, 0.0d, List.of(), System.nanoTime());
        }
    }

    @Override
    public void onTickStart(long tickNumber) {
    }

    @Override
    public void onTickEnd(long tickNumber,
                          long durationNanos,
                          int activeBodies,
                          int totalBodies,
                          int staticBodies,
                          int maxBodies,
                          long droppedSnapshots,
                          long overwrittenSnapshots) {
    }

    @Override
    public void onDiagnostics(LoopDiagnosticsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        worldAccumulators
                .computeIfAbsent(snapshot.worldName(), ignored -> new WorldAccumulator(snapshot.worldId(), snapshot.worldName()))
                .update(snapshot);
    }

    private static final class WorldAccumulator {
        private final UUID worldId;
        private final String worldName;
        private final ReentrantLock lock = new ReentrantLock();
        private final long[] tickDurationNanosWindow = new long[WINDOW_TICKS];
        private int index;
        private int size;
        private long lastTick;
        private long lastTimestampNanos;
        private int pendingSnapshots;
        private long droppedSnapshots;
        private long overwrittenSnapshots;
        private long backlogTicks;
        private long overloadedTicks;
        private int activeBodies;
        private int totalBodies;
        private int staticBodies;
        private int maxBodies;
        private volatile boolean overloaded;

        private WorldAccumulator(UUID worldId, String worldName) {
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.worldName = Objects.requireNonNull(worldName, "worldName");
        }

        private void update(LoopDiagnosticsSnapshot snapshot) {
            lock.lock();
            try {
                tickDurationNanosWindow[index] = snapshot.durationNanos();
                index = (index + 1) % WINDOW_TICKS;
                if (size < WINDOW_TICKS) {
                    size++;
                }
                lastTick = snapshot.tickNumber();
                lastTimestampNanos = snapshot.sampleTimestampNanos();
                pendingSnapshots = snapshot.pendingSnapshots();
                droppedSnapshots = snapshot.droppedSnapshots();
                overwrittenSnapshots = snapshot.overwrittenSnapshots();
                backlogTicks = snapshot.backlogTicks();
                overloadedTicks = snapshot.overloadedTicks();
                activeBodies = snapshot.activeBodies();
                totalBodies = snapshot.totalBodies();
                staticBodies = snapshot.staticBodies();
                maxBodies = snapshot.maxBodies();
                overloaded = snapshot.overloaded();
            } finally {
                lock.unlock();
            }
        }

        private WorldHealthSnapshot snapshot(MetricsServiceSnapshot metricsSnapshot) {
            Objects.requireNonNull(metricsSnapshot, "metricsSnapshot");
            lock.lock();
            try {
                long[] samples = Arrays.copyOf(tickDurationNanosWindow, size);
                Arrays.sort(samples);
                TickDurationPercentiles percentiles = new TickDurationPercentiles(
                        toMillis(percentile(samples, 0.50d)),
                        toMillis(percentile(samples, 0.90d)),
                        toMillis(percentile(samples, 0.95d)),
                        toMillis(percentile(samples, 0.99d)),
                        toMillis(samples.length == 0 ? 0L : samples[samples.length - 1])
                );
                QueueBackpressureCounters backpressureCounters = new QueueBackpressureCounters(
                        pendingSnapshots,
                        Math.max(droppedSnapshots, metricsSnapshot.droppedSnapshots()),
                        Math.max(overwrittenSnapshots, metricsSnapshot.overwrittenSnapshots()),
                        backlogTicks,
                        overloadedTicks,
                        metricsSnapshot.oneTimeQueueDepth(),
                        metricsSnapshot.recurringQueueDepth()
                );
                BodyCounters bodyCounters = new BodyCounters(
                        Math.max(activeBodies, metricsSnapshot.activeBodyCount()),
                        Math.max(totalBodies, metricsSnapshot.totalBodyCount()),
                        Math.max(staticBodies, metricsSnapshot.staticBodyCount()),
                        Math.max(maxBodies, metricsSnapshot.maxBodyLimit())
                );
                return new WorldHealthSnapshot(
                        worldId,
                        worldName,
                        overloaded,
                        percentiles,
                        backpressureCounters,
                        bodyCounters,
                        lastTick,
                        lastTimestampNanos
                );
            } finally {
                lock.unlock();
            }
        }

        private static long percentile(long[] sorted, double percentile) {
            if (sorted.length == 0) {
                return 0L;
            }
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            int boundedIndex = Math.max(0, Math.min(sorted.length - 1, index));
            return sorted[boundedIndex];
        }

        private static double toMillis(long nanos) {
            return nanos / 1_000_000.0d;
        }
    }

    private static final class TransportWorldAccumulator {
        private final UUID worldId;
        private final String worldName;
        private int transports;
        private int activeTransports;
        private int groundedTransports;
        private double totalSpeedKmh;
        private double maxSpeedKmh;
        private double totalRpm;

        private TransportWorldAccumulator(UUID worldId, String worldName) {
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.worldName = Objects.requireNonNull(worldName, "worldName");
        }

        private void accumulate(TransportState state) {
            transports++;
            if (state.speedKmh() > 0.01d) {
                activeTransports++;
            }
            if (state.grounded()) {
                groundedTransports++;
            }
            totalSpeedKmh += state.speedKmh();
            maxSpeedKmh = Math.max(maxSpeedKmh, state.speedKmh());
            totalRpm += state.engineRpm();
        }

        private TransportWorldSnapshot snapshot() {
            double avgSpeed = transports == 0 ? 0.0d : totalSpeedKmh / transports;
            double avgRpm = transports == 0 ? 0.0d : totalRpm / transports;
            return new TransportWorldSnapshot(
                    worldId,
                    worldName,
                    transports,
                    activeTransports,
                    groundedTransports,
                    avgSpeed,
                    maxSpeedKmh,
                    avgRpm
            );
        }
    }
}
