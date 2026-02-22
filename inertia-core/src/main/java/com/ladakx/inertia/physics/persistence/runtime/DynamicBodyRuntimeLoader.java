package com.ladakx.inertia.physics.persistence.runtime;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageFile;
import com.ladakx.inertia.physics.persistence.storage.DynamicBodyStorageRecord;
import com.ladakx.inertia.physics.persistence.storage.PartState;
import com.ladakx.inertia.physics.persistence.validation.DynamicBodyValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DynamicBodyRuntimeLoader {

    private static final int DEFAULT_BATCH_SIZE = 256;

    private final InertiaPlugin plugin;
    private final BodyFactory bodyFactory;
    private final DynamicBodyValidator validator;
    private final DynamicBodyStorageFile storageFile;
    private final Executor ioExecutor;
    private final int batchSize;

    private final Map<DynamicBodyChunkKey, ConcurrentLinkedQueue<DynamicBodyStorageRecord>> pendingByChunk = new ConcurrentHashMap<>();
    private final Set<java.util.UUID> loadedObjectIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile BukkitTask flushTask;

    public DynamicBodyRuntimeLoader(InertiaPlugin plugin,
                                    BodyFactory bodyFactory,
                                    DynamicBodyValidator validator,
                                    Path storagePath,
                                    int batchSize) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bodyFactory = Objects.requireNonNull(bodyFactory, "bodyFactory");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.storageFile = new DynamicBodyStorageFile(Objects.requireNonNull(storagePath, "storagePath"));
        this.batchSize = Math.max(1, batchSize);
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "inertia-dynamic-body-loader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public DynamicBodyRuntimeLoader(InertiaPlugin plugin,
                                    BodyFactory bodyFactory,
                                    DynamicBodyValidator validator,
                                    Path storagePath) {
        this(plugin, bodyFactory, validator, storagePath, DEFAULT_BATCH_SIZE);
    }

    public CompletableFuture<Void> loadAsync() {
        if (!loading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(storageFile::read, ioExecutor)
                .thenAccept(records -> {
                    List<DynamicBodyStorageRecord> valid = new ArrayList<>(records.size());
                    for (DynamicBodyStorageRecord record : records) {
                        if (validator.isValid(record)) {
                            valid.add(record);
                        }
                    }

                    for (DynamicBodyStorageRecord record : valid) {
                        DynamicBodyChunkKey key = new DynamicBodyChunkKey(record.world(), record.chunkX(), record.chunkZ());
                        pendingByChunk.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>()).offer(record);
                    }
                })
                .whenComplete((unused, throwable) -> {
                    loading.set(false);
                    if (throwable != null) {
                        InertiaLogger.error("Dynamic body async load failed", throwable);
                    }
                    scheduleFlushTask();
                    flushLoadedChunks();
                });
    }

    public void onChunkLoaded(String world, int chunkX, int chunkZ) {
        Objects.requireNonNull(world, "world");
        DynamicBodyChunkKey key = new DynamicBodyChunkKey(world, chunkX, chunkZ);
        ConcurrentLinkedQueue<DynamicBodyStorageRecord> queue = pendingByChunk.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        scheduleFlushTask();
    }

    public void clear() {
        pendingByChunk.clear();
        loadedObjectIds.clear();
        BukkitTask task = flushTask;
        if (task != null) {
            task.cancel();
        }
        flushTask = null;
    }

    private void flushLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            org.bukkit.Chunk[] chunks = world.getLoadedChunks();
            for (org.bukkit.Chunk chunk : chunks) {
                onChunkLoaded(world.getName(), chunk.getX(), chunk.getZ());
            }
        }
    }

    private void scheduleFlushTask() {
        if (flushTask != null) {
            return;
        }
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushBatchOnMainThread, 1L, 1L);
    }

    private void flushBatchOnMainThread() {
        int remaining = batchSize;
        boolean hasPending = false;
        for (Map.Entry<DynamicBodyChunkKey, ConcurrentLinkedQueue<DynamicBodyStorageRecord>> entry : pendingByChunk.entrySet()) {
            DynamicBodyChunkKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                if (!entry.getValue().isEmpty()) hasPending = true;
                continue;
            }

            com.ladakx.inertia.physics.world.PhysicsWorld space = plugin.getWorldRegistry().getWorld(world);
            if (space == null) continue;

            ConcurrentLinkedQueue<DynamicBodyStorageRecord> queue = entry.getValue();
            DynamicBodyStorageRecord record;

            while (remaining > 0 && (record = queue.poll()) != null) {
                if (loadedObjectIds.add(record.clusterId())) {
                    Map<String, Object> params = new java.util.HashMap<>();
                    params.put("bypass_validation", true);
                    params.put("cluster_id", record.clusterId());
                    if (record.customData().containsKey("size")) params.put("size", Integer.parseInt(record.customData().get("size")));
                    if (record.customData().containsKey("skin")) params.put("skinNickname", record.customData().get("skin"));
                    if (record.customData().containsKey("force")) params.put("force", Float.parseFloat(record.customData().get("force")));
                    if (record.customData().containsKey("fuse")) params.put("fuse", Integer.parseInt(record.customData().get("fuse")));
                    params.put("restore_parts", record.parts());

                    PartState rootPart = record.parts().isEmpty() ? null : record.parts().get(0);
                    if (rootPart == null) continue;

                    Location spawnLoc = new Location(world, rootPart.x() + space.getOrigin().xx(), rootPart.y() + space.getOrigin().yy(), rootPart.z() + space.getOrigin().zz());

                    InertiaPhysicsBody rootBody = bodyFactory.spawnBodyWithResult(spawnLoc, record.bodyId(), null, params);
                    if (rootBody != null) {
                        for (InertiaPhysicsBody b : space.getBodies()) {
                            if (b instanceof AbstractPhysicsBody ab && record.clusterId().equals(ab.getClusterId())) {
                                ab.setFriction(record.friction());
                                ab.setRestitution(record.restitution());
                                ab.setGravityFactor(record.gravityFactor());
                                ab.setMotionType(record.motionType());

                                PartState ps = null;
                                for (PartState state : record.parts()) {
                                    if (state.partKey().equals(ab.getPartKey())) { ps = state; break; }
                                }

                                if (ps != null) {
                                    space.getBodyInterface().setPositionAndRotation(
                                            ab.getBody().getId(),
                                            new RVec3(ps.x(), ps.y(), ps.z()),
                                            new Quat(ps.rX(), ps.rY(), ps.rZ(), ps.rW()),
                                            com.github.stephengold.joltjni.enumerate.EActivation.Activate
                                    );
                                    ab.getBody().setLinearVelocity(new com.github.stephengold.joltjni.Vec3((float)ps.lvX(), (float)ps.lvY(), (float)ps.lvZ()));
                                    ab.getBody().setAngularVelocity(new com.github.stephengold.joltjni.Vec3((float)ps.avX(), (float)ps.avY(), (float)ps.avZ()));

                                    if (ps.anchored() && ab instanceof com.ladakx.inertia.physics.body.impl.ChainPhysicsBody chainLink) {
                                        com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry.BodyModel model = plugin.getConfigManager().getPhysicsBodyRegistry().require(record.bodyId());
                                        if (model.bodyDefinition() instanceof com.ladakx.inertia.physics.body.config.ChainBodyDefinition def) {
                                            anchorToWorldInLoader(space, chainLink, new RVec3(ps.anchorX(), ps.anchorY(), ps.anchorZ()), def);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                remaining--;
            }

            if (queue.isEmpty()) pendingByChunk.remove(key, queue);
            else hasPending = true;

            if (remaining == 0) break;
        }

        if (!hasPending && pendingByChunk.isEmpty()) {
            BukkitTask task = flushTask;
            if (task != null) task.cancel();
            flushTask = null;
        }
    }

    private void anchorToWorldInLoader(com.ladakx.inertia.physics.world.PhysicsWorld world, com.ladakx.inertia.physics.body.impl.ChainPhysicsBody link, RVec3 anchorPos, com.ladakx.inertia.physics.body.config.ChainBodyDefinition def) {
        com.github.stephengold.joltjni.Body fixedBody = com.github.stephengold.joltjni.Body.sFixedToWorld();
        com.github.stephengold.joltjni.SixDofConstraintSettings settings = new com.github.stephengold.joltjni.SixDofConstraintSettings();
        settings.setSpace(com.github.stephengold.joltjni.enumerate.EConstraintSpace.WorldSpace);
        settings.setPosition1(anchorPos);
        settings.setPosition2(anchorPos);
        settings.makeFixedAxis(com.github.stephengold.joltjni.enumerate.EAxis.TranslationX);
        settings.makeFixedAxis(com.github.stephengold.joltjni.enumerate.EAxis.TranslationY);
        settings.makeFixedAxis(com.github.stephengold.joltjni.enumerate.EAxis.TranslationZ);
        float swingRad = (float) Math.toRadians(def.limits().swingLimitAngle());
        settings.setLimitedAxis(com.github.stephengold.joltjni.enumerate.EAxis.RotationX, -swingRad, swingRad);
        settings.setLimitedAxis(com.github.stephengold.joltjni.enumerate.EAxis.RotationZ, -swingRad, swingRad);
        switch (def.limits().twistMode()) {
            case LOCKED -> settings.makeFixedAxis(com.github.stephengold.joltjni.enumerate.EAxis.RotationY);
            case LIMITED -> settings.setLimitedAxis(com.github.stephengold.joltjni.enumerate.EAxis.RotationY, -0.1f, 0.1f);
            case FREE -> settings.makeFreeAxis(com.github.stephengold.joltjni.enumerate.EAxis.RotationY);
        }
        com.github.stephengold.joltjni.TwoBodyConstraint constraint = settings.create(fixedBody, link.getBody());
        world.addConstraint(constraint);
        link.addRelatedConstraint(constraint.toRef());
        world.getBodyInterface().activateBody(link.getBody().getId());
        link.setAnchored(true);
        link.setWorldAnchor(anchorPos);
    }
}
