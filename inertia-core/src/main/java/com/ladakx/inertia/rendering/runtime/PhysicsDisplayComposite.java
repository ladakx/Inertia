package com.ladakx.inertia.rendering.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.api.rendering.transform.EntityTransformAlgorithm;
import com.ladakx.inertia.api.rendering.transform.RenderTransformService;
import com.ladakx.inertia.core.impl.rendering.transform.MutableEntityTransformContext;
import com.ladakx.inertia.core.impl.rendering.transform.MutableEntityTransformImpl;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.staticent.StaticEntityMetadata;
import com.ladakx.inertia.rendering.staticent.StaticEntityPersister;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PhysicsDisplayComposite {

    public record DisplayPart(
            String modelId,
            boolean syncPosition,
            boolean syncRotation,
            @Nullable com.ladakx.inertia.rendering.version.ClientVersionRange clientRange,
            RenderEntityDefinition definition,
            NetworkVisual visual
    ) {
        public DisplayPart {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(definition);
            Objects.requireNonNull(visual);
        }
    }

    private final AbstractPhysicsBody owner;
    private final Body body;
    private final List<DisplayPart> parts;
    private final World world;
    private final @Nullable NetworkEntityTracker tracker;
    private final @Nullable RenderTransformService transformService;
    private final StaticEntityPersister staticEntityPersister;

    private final Vector3f cacheBodyPos = new Vector3f();
    private final Quaternionf cacheBodyRot = new Quaternionf();
    private final Vector3f cacheCenterOffset = new Vector3f();
    private final Vector3f cacheLocalOffset = new Vector3f();
    private final Vector3f cacheStackTranslation = new Vector3f();
    private final Quaternionf cacheStackRotation = new Quaternionf();
    private final MutableEntityTransformContext transformContext = new MutableEntityTransformContext();
    private final MutableEntityTransformImpl transformOutput = new MutableEntityTransformImpl();

    private final int[] orderedPartIndices;
    private final int[] placedOnIndex;
    private final Vector3f[] partWorldPos;
    private final Quaternionf[] partWorldRot;

    public PhysicsDisplayComposite(AbstractPhysicsBody owner,
                                   World world,
                                   List<DisplayPart> parts,
                                   @Nullable NetworkEntityTracker tracker,
                                   @Nullable RenderTransformService transformService,
                                   StaticEntityPersister staticEntityPersister) {
        this.owner = Objects.requireNonNull(owner);
        this.body = owner.getBody();
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);
        this.tracker = tracker;
        this.transformService = transformService;
        this.staticEntityPersister = Objects.requireNonNull(staticEntityPersister, "staticEntityPersister");
        this.placedOnIndex = new int[parts.size()];
        this.orderedPartIndices = computePlaceOrder();
        this.partWorldPos = new Vector3f[parts.size()];
        this.partWorldRot = new Quaternionf[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            this.partWorldPos[i] = new Vector3f();
            this.partWorldRot[i] = new Quaternionf();
        }
        registerAll();
    }

    private void registerAll() {
        if (tracker == null) return;

        RVec3 pos = body.getPosition();
        Quat rot = body.getRotation();
        RVec3 origin = owner.getSpace().getOrigin();

        Location baseLoc = new Location(world, pos.xx() + origin.xx(), pos.yy() + origin.yy(), pos.zz() + origin.zz());
        Quaternionf baseRot = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());

        boolean sleepingNow = !body.isActive();
        int groupKey = -1;
        for (int idx : orderedPartIndices) {
            if (placedOnIndex[idx] < 0) {
                groupKey = parts.get(idx).visual().getId();
                break;
            }
        }
        if (groupKey < 0) {
            groupKey = parts.isEmpty() ? -1 : parts.get(0).visual().getId();
        }
        List<NetworkEntityTracker.VisualRegistration> registrations = new ArrayList<>(parts.size());

        cacheBodyPos.set((float) baseLoc.getX(), (float) baseLoc.getY(), (float) baseLoc.getZ());
        cacheBodyRot.set(baseRot);
        cacheCenterOffset.set(0, 0, 0);

        for (int idx : orderedPartIndices) {
            DisplayPart part = parts.get(idx);
            RenderEntityDefinition def = part.definition();

            int parentIdx = placedOnIndex[idx];
            Vector3f basePos = (parentIdx < 0) ? cacheBodyPos : partWorldPos[parentIdx];
            Quaternionf baseQ = (parentIdx < 0) ? cacheBodyRot : partWorldRot[parentIdx];

            Vector3f outPos = partWorldPos[idx];
            Quaternionf outRot = partWorldRot[idx];
            computeWorldTransform(part, def, parentIdx >= 0, basePos, baseQ, outPos, outRot);

            Location spawnLoc = new Location(world, outPos.x, outPos.y, outPos.z);
            int mountVehicleId = parentIdx < 0 ? -1 : parts.get(parentIdx).visual().getId();
            int allowedLodMask = computeAllowedLodMask(def);
            boolean enabled = computeEnabled(def, sleepingNow);
            registrations.add(new NetworkEntityTracker.VisualRegistration(
                    part.visual(),
                    spawnLoc,
                    outRot,
                    part.clientRange(),
                    groupKey,
                    mountVehicleId,
                    true,
                    allowedLodMask,
                    enabled
            ));

            owner.getSpace().registerNetworkEntityId(owner, part.visual().getId());
        }
        tracker.registerBatch(registrations);
    }

    public void capture(boolean sleeping, RVec3 origin, List<VisualState> accumulator, SnapshotPool pool) {
        capture(sleeping, origin, accumulator, pool, false);
    }

    public void capture(boolean sleeping, RVec3 origin, List<VisualState> accumulator, SnapshotPool pool, boolean forceVisibilitySync) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(accumulator, "accumulator");
        Objects.requireNonNull(pool, "pool");
        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();
        captureWithTransform(
                sleeping,
                origin,
                accumulator,
                pool,
                (float) bodyPosJolt.xx(),
                (float) bodyPosJolt.yy(),
                (float) bodyPosJolt.zz(),
                bodyRotJolt.getX(),
                bodyRotJolt.getY(),
                bodyRotJolt.getZ(),
                bodyRotJolt.getW(),
                forceVisibilitySync
        );
    }


    public void captureWithTransform(boolean sleeping,
                                     RVec3 origin,
                                     List<VisualState> accumulator,
                                     SnapshotPool pool,
                                     float positionX,
                                     float positionY,
                                     float positionZ,
                                     float rotationX,
                                     float rotationY,
                                     float rotationZ,
                                     float rotationW,
                                     boolean forceVisibilitySync) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(accumulator, "accumulator");
        Objects.requireNonNull(pool, "pool");
        if (parts.isEmpty()) return;

        cacheBodyPos.set(
                positionX + (float) origin.xx(),
                positionY + (float) origin.yy(),
                positionZ + (float) origin.zz()
        );
        cacheBodyRot.set(rotationX, rotationY, rotationZ, rotationW);
        cacheCenterOffset.set(0, 0, 0);

        for (int idx : orderedPartIndices) {
            DisplayPart part = parts.get(idx);
            RenderEntityDefinition def = part.definition();

            int parentIdx = placedOnIndex[idx];
            Vector3f basePos = (parentIdx < 0) ? cacheBodyPos : partWorldPos[parentIdx];
            Quaternionf baseQ = (parentIdx < 0) ? cacheBodyRot : partWorldRot[parentIdx];

            Vector3f outPos = partWorldPos[idx];
            Quaternionf outRot = partWorldRot[idx];
            computeWorldTransform(part, def, parentIdx >= 0, basePos, baseQ, outPos, outRot);

            boolean enabled = computeEnabled(def, sleeping);
            if (!enabled && !forceVisibilitySync) continue;

            VisualState state = pool.borrowState();
            state.set(
                    part.visual(),
                    outPos,
                    outRot,
                    cacheCenterOffset,
                    def.rotTranslation(),
                    enabled
            );
            accumulator.add(state);
        }
    }

    public void setGlowing(boolean glowing) {
        for (DisplayPart part : parts) {
            part.visual().setGlowing(glowing);
            if (tracker != null) {
                tracker.updateMetadata(part.visual());
            }
        }
    }

    public void markAsStatic(@Nullable UUID clusterId) {
        if (parts.isEmpty()) return;

        boolean sleeping = !body.isActive();
        RVec3 origin = owner.getSpace().getOrigin();
        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        Location spawnLoc = new Location(world, 0, 0, 0);

        for (int idx : orderedPartIndices) {
            DisplayPart part = parts.get(idx);
            RenderEntityDefinition def = part.definition();
            NetworkVisual visual = part.visual();

            int parentIdx = placedOnIndex[idx];
            Vector3f basePos = (parentIdx < 0) ? cacheBodyPos : partWorldPos[parentIdx];
            Quaternionf baseQ = (parentIdx < 0) ? cacheBodyRot : partWorldRot[parentIdx];

            Vector3f outPos = partWorldPos[idx];
            Quaternionf outRot = partWorldRot[idx];
            computeWorldTransform(part, def, parentIdx >= 0, basePos, baseQ, outPos, outRot);

            boolean enabled = computeEnabled(def, sleeping);
            if (!enabled) {
                cleanupNetworkVisual(visual);
                continue;
            }

            spawnLoc.setX(outPos.x);
            spawnLoc.setY(outPos.y);
            spawnLoc.setZ(outPos.z);

            try {
                StaticEntityMetadata metadata = new StaticEntityMetadata(
                        owner.getBodyId(),
                        owner.getUuid(),
                        part.modelId(),
                        def.key(),
                        clusterId
                );
                // Static entities exist on server only; persist only variants compatible with server version.
                com.ladakx.inertia.rendering.version.ClientVersionRange range = part.clientRange();
                int serverProtocol = com.ladakx.inertia.common.MinecraftVersions.CURRENT != null
                        ? com.ladakx.inertia.common.MinecraftVersions.CURRENT.networkProtocol
                        : Integer.MAX_VALUE;
                if (range == null || range.containsProtocol(serverProtocol)) {
                    staticEntityPersister.persist(spawnLoc, def, outRot, metadata);
                }
            } finally {
                cleanupNetworkVisual(visual);
            }
        }
    }

    private void computeWorldTransform(DisplayPart part,
                                       RenderEntityDefinition def,
                                       boolean isPassenger,
                                       Vector3f basePos,
                                       Quaternionf baseRot,
                                       Vector3f outPos,
                                       Quaternionf outRot) {
        Vector offset = def.localOffset();
        cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
        transformContext.set(
                part.modelId(),
                def.key(),
                isPassenger,
                isPassenger,
                part.syncPosition() && !isPassenger,
                part.syncRotation(),
                basePos,
                baseRot,
                cacheLocalOffset,
                def.localRotation(),
                cacheStackTranslation.zero(),
                cacheStackRotation.identity()
        );
        EntityTransformAlgorithm algorithm = resolveAlgorithm(part.modelId(), def.key());
        algorithm.compute(transformContext, transformOutput);
        outPos.set(transformOutput.position());
        outRot.set(transformOutput.rotation());
    }

    private EntityTransformAlgorithm resolveAlgorithm(String modelId, String entityKey) {
        if (transformService == null) {
            return DEFAULT_ALGORITHM_HOLDER.DEFAULT_ALGORITHM;
        }
        return transformService.resolveAlgorithm(modelId, entityKey);
    }

    private static final class DEFAULT_ALGORITHM_HOLDER {
        private static final EntityTransformAlgorithm DEFAULT_ALGORITHM =
                new com.ladakx.inertia.core.impl.rendering.transform.DefaultEntityTransformAlgorithm();
    }

    private int[] computePlaceOrder() {
        int n = parts.size();
        if (n == 0) return new int[0];

        for (int i = 0; i < n; i++) {
            placedOnIndex[i] = -1;
        }

        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            DisplayPart part = parts.get(i);
            groups.computeIfAbsent(part.modelId(), k -> new ArrayList<>()).add(i);
        }

        List<Integer> globalOrder = new ArrayList<>(n);

        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            String modelId = entry.getKey();
            List<Integer> indices = entry.getValue();

            Map<String, Integer> indexByKey = new LinkedHashMap<>();
            for (int idx : indices) {
                String key = parts.get(idx).definition().key();
                if (indexByKey.putIfAbsent(key, idx) != null) {
                    InertiaLogger.warn("Render model '" + modelId + "': duplicate entity key '" + key + "' in composite; 'place' may be ambiguous");
                }
            }

            Map<Integer, Integer> indegree = new LinkedHashMap<>();
            Map<Integer, List<Integer>> children = new LinkedHashMap<>();
            for (int idx : indices) {
                indegree.put(idx, 0);
                children.put(idx, new ArrayList<>());
            }

            for (int idx : indices) {
                RenderEntityDefinition def = parts.get(idx).definition();
                String parentKey = def.placeOn();
                if (parentKey == null) continue;
                Integer parentIdx = indexByKey.get(parentKey);
                if (parentIdx == null) {
                    InertiaLogger.warn("Render model '" + modelId + "': entity '" + def.key() + "' place target '" + parentKey + "' not found");
                    continue;
                }
                if (parentIdx == idx) {
                    InertiaLogger.warn("Render model '" + modelId + "': entity '" + def.key() + "' cannot place on itself");
                    continue;
                }
                placedOnIndex[idx] = parentIdx;
                children.get(parentIdx).add(idx);
                indegree.put(idx, indegree.get(idx) + 1);
            }

            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int idx : indices) {
                if (indegree.get(idx) == 0) queue.add(idx);
            }

            List<Integer> localOrder = new ArrayList<>(indices.size());
            while (!queue.isEmpty()) {
                int idx = queue.removeFirst();
                localOrder.add(idx);
                for (int child : children.get(idx)) {
                    int d = indegree.get(child) - 1;
                    indegree.put(child, d);
                    if (d == 0) queue.addLast(child);
                }
            }

            if (localOrder.size() != indices.size()) {
                // Cycle: drop parent links for the remaining ones.
                java.util.Set<Integer> inOrder = new java.util.HashSet<>(localOrder);
                for (int idx : indices) {
                    if (inOrder.contains(idx)) continue;
                    RenderEntityDefinition def = parts.get(idx).definition();
                    InertiaLogger.warn("Render model '" + modelId + "': cyclic 'place' detected for entity '" + def.key() + "', ignoring its place");
                    placedOnIndex[idx] = -1;
                    localOrder.add(idx);
                }
            }

            globalOrder.addAll(localOrder);
        }

        int[] out = new int[globalOrder.size()];
        for (int i = 0; i < globalOrder.size(); i++) {
            out[i] = globalOrder.get(i);
        }
        return out;
    }

    private boolean computeEnabled(RenderEntityDefinition def, boolean sleeping) {
        boolean show = sleeping ? def.showWhenSleeping() : def.showWhenActive();
        boolean hide = sleeping ? def.hideWhenSleeping() : def.hideWhenActive();
        return show && !hide;
    }

    private int computeAllowedLodMask(RenderEntityDefinition def) {
        int all = 0x07;
        int show = def.showLodMask() & 0x07;
        if (show == 0) {
            show = all;
        }
        int hidden = def.hideLodMask() & 0x07;
        return (show & ~hidden) & 0x07;
    }

    public boolean isValid() {
        return body != null && !parts.isEmpty();
    }

    public void destroy() {
        List<Integer> ids = new ArrayList<>(parts.size());
        for (DisplayPart part : parts) {
            int visualId = part.visual().getId();
            ids.add(visualId);
            try {
                owner.getSpace().unregisterNetworkEntityId(visualId);
            } catch (Exception ignored) {
            }
        }

        if (tracker != null && !ids.isEmpty()) {
            tracker.unregisterBatch(ids);
        }
    }

    private void cleanupNetworkVisual(NetworkVisual visual) {
        if (tracker != null) {
            // Tracker handles packet buffering for removal
            tracker.unregister(visual);
        }
        try {
            owner.getSpace().unregisterNetworkEntityId(visual.getId());
        } catch (Exception ignored) {
        }
    }
}
