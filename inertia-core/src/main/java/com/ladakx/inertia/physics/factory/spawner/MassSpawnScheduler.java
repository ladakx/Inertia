package com.ladakx.inertia.physics.factory.spawner;

import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

public class MassSpawnScheduler {

    private final InertiaPlugin plugin;
    private final Settings settings;
    private final BiFunction<Location, SpawnJob, Boolean> spawnAction;

    private final Deque<SpawnJob> pending = new ArrayDeque<>();
    private final Map<UUID, SpawnJob> jobs = new HashMap<>();

    private final Map<String, Integer> worldActiveJobs = new HashMap<>();
    private final Map<UUID, Integer> playerActiveJobs = new HashMap<>();

    private BukkitTask task;
    private long schedulerTicks = 0;
    private int stableTpsTicks = 0;
    private int dynamicBudget;

    public MassSpawnScheduler(InertiaPlugin plugin,
                              Settings settings,
                              BiFunction<Location, SpawnJob, Boolean> spawnAction) {
        this.plugin = plugin;
        this.settings = settings;
        this.spawnAction = spawnAction;
        this.dynamicBudget = settings.baseBudgetPerTick();
    }

    public synchronized EnqueueResult enqueue(Player player,
                                              PhysicsWorld world,
                                              Location center,
                                              String bodyId,
                                              List<Vector> offsets) {
        String worldKey = world.getWorldBukkit().getName();
        UUID playerId = player.getUniqueId();

        int worldCount = worldActiveJobs.getOrDefault(worldKey, 0);
        if (worldCount >= settings.maxConcurrentJobsPerWorld()) {
            return EnqueueResult.rejected("World throttle reached (" + settings.maxConcurrentJobsPerWorld() + ")");
        }

        int playerCount = playerActiveJobs.getOrDefault(playerId, 0);
        if (playerCount >= settings.maxConcurrentJobsPerPlayer()) {
            return EnqueueResult.rejected("Player throttle reached (" + settings.maxConcurrentJobsPerPlayer() + ")");
        }

        SpawnJob job = new SpawnJob(
                UUID.randomUUID(),
                world,
                player,
                center.clone(),
                bodyId,
                List.copyOf(offsets),
                worldKey
        );

        jobs.put(job.jobId(), job);
        pending.addLast(job);
        worldActiveJobs.merge(worldKey, 1, Integer::sum);
        playerActiveJobs.merge(playerId, 1, Integer::sum);

        ensureTaskStarted();

        return EnqueueResult.accepted(snapshot(job.jobId()));
    }

    public synchronized @Nullable JobSnapshot snapshot(UUID jobId) {
        SpawnJob job = jobs.get(jobId);
        return job == null ? null : job.toSnapshot();
    }

    public synchronized void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
        jobs.clear();
        worldActiveJobs.clear();
        playerActiveJobs.clear();
    }

    private void ensureTaskStarted() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        synchronized (this) {
            schedulerTicks++;

            if (pending.isEmpty()) {
                if (task != null) {
                    task.cancel();
                    task = null;
                }
                return;
            }

            int budget = computeBudget();
            while (budget > 0 && !pending.isEmpty()) {
                SpawnJob job = pending.pollFirst();
                if (job == null) break;

                int localBudget = Math.min(budget, settings.maxSpawnsPerJobPerTick());
                int spent = runJobChunk(job, localBudget);
                budget -= spent;

                if (!job.isFinished()) {
                    pending.addLast(job);
                } else {
                    finishJob(job);
                }
            }
        }
    }

    private int runJobChunk(SpawnJob job, int maxItems) {
        int spent = 0;
        while (spent < maxItems && job.hasRemaining()) {
            if (!job.world().canSpawnBodies(1)) {
                job.markFinished("World body limit reached");
                break;
            }

            Vector offset = job.nextOffset();
            Location spawnAt = job.center().clone().add(offset);
            boolean success = spawnAction.apply(spawnAt, job);
            job.markAttempt(success);
            spent++;

            if (Bukkit.getWorld(job.worldKey()) == null) {
                job.markFinished("World is invalid");
                break;
            }
        }

        if (!job.hasRemaining()) {
            job.markFinished(null);
        }
        return spent;
    }

    private int computeBudget() {
        if (schedulerTicks <= settings.warmupTicks()) {
            return settings.warmupBudgetPerTick();
        }

        double mspt = Bukkit.getAverageTickTime();
        double tps = mspt <= 0 ? 20.0 : Math.min(20.0, 1000.0 / mspt);

        if (tps >= settings.stableTpsThreshold()) {
            stableTpsTicks++;
            if (stableTpsTicks >= settings.stableTicksToIncreaseBudget()) {
                dynamicBudget = Math.min(settings.maxBudgetPerTick(), dynamicBudget + settings.budgetIncreaseStep());
                stableTpsTicks = 0;
            }
        } else {
            stableTpsTicks = 0;
            dynamicBudget = Math.max(settings.minBudgetPerTick(), dynamicBudget - settings.budgetDecreaseStep());
        }

        return dynamicBudget;
    }

    private void finishJob(SpawnJob job) {
        worldActiveJobs.computeIfPresent(job.worldKey(), (k, v) -> v <= 1 ? null : v - 1);
        playerActiveJobs.computeIfPresent(job.player().getUniqueId(), (k, v) -> v <= 1 ? null : v - 1);
    }

    public record EnqueueResult(boolean accepted,
                                @Nullable JobSnapshot snapshot,
                                @Nullable String rejectReason) {
        public static EnqueueResult accepted(@Nullable JobSnapshot snapshot) {
            return new EnqueueResult(true, snapshot, null);
        }

        public static EnqueueResult rejected(String reason) {
            return new EnqueueResult(false, null, reason);
        }
    }

    public record JobSnapshot(UUID jobId,
                              int total,
                              int attempted,
                              int success,
                              double progress,
                              boolean completed,
                              @Nullable String completionReason) {
    }

    public record Settings(int minBudgetPerTick,
                           int baseBudgetPerTick,
                           int maxBudgetPerTick,
                           int warmupBudgetPerTick,
                           int warmupTicks,
                           int budgetIncreaseStep,
                           int budgetDecreaseStep,
                           double stableTpsThreshold,
                           int stableTicksToIncreaseBudget,
                           int maxConcurrentJobsPerWorld,
                           int maxConcurrentJobsPerPlayer,
                           int maxSpawnsPerJobPerTick) {
    }

    public static final class SpawnJob {
        private final UUID jobId;
        private final PhysicsWorld world;
        private final Player player;
        private final Location center;
        private final String bodyId;
        private final List<Vector> offsets;
        private final String worldKey;

        private int cursor = 0;
        private int attempted = 0;
        private int success = 0;
        private boolean finished = false;
        private String completionReason;

        private SpawnJob(UUID jobId,
                         PhysicsWorld world,
                         Player player,
                         Location center,
                         String bodyId,
                         List<Vector> offsets,
                         String worldKey) {
            this.jobId = jobId;
            this.world = world;
            this.player = player;
            this.center = center;
            this.bodyId = bodyId;
            this.offsets = offsets;
            this.worldKey = worldKey;
        }

        public UUID jobId() { return jobId; }

        public PhysicsWorld world() { return world; }

        public Player player() { return player; }

        public Location center() { return center; }

        public String bodyId() { return bodyId; }

        public String worldKey() { return worldKey; }

        private boolean hasRemaining() {
            return cursor < offsets.size();
        }

        private Vector nextOffset() {
            return offsets.get(cursor++);
        }

        private void markAttempt(boolean spawnSuccess) {
            attempted++;
            if (spawnSuccess) {
                success++;
            }
        }

        private void markFinished(@Nullable String reason) {
            this.finished = true;
            this.completionReason = reason;
        }

        private boolean isFinished() {
            return finished;
        }

        private JobSnapshot toSnapshot() {
            double progress = offsets.isEmpty() ? 1.0 : Math.min(1.0, (double) attempted / (double) offsets.size());
            return new JobSnapshot(jobId, offsets.size(), attempted, success, progress, finished, completionReason);
        }
    }
}
