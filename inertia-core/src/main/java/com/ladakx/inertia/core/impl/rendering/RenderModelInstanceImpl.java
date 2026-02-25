package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.rendering.entity.RenderEntity;
import com.ladakx.inertia.api.rendering.entity.RenderModelInstance;
import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;
import java.util.Objects;

final class RenderModelInstanceImpl implements RenderModelInstance {

    private final String modelId;
    private final Map<String, RenderEntityImpl> entitiesByKey;
    private final List<RenderEntityImpl> orderedEntities;
    private final Map<RenderEntityImpl, RenderEntityImpl> placedOn;
    private final Location baseLocation = new Location(null, 0, 0, 0);
    private final Quaternionf baseRotation = new Quaternionf();
    private boolean enabled = true;
    private boolean closed = false;

    RenderModelInstanceImpl(@NotNull String modelId, @NotNull Map<String, RenderEntityImpl> entitiesByKey) {
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.entitiesByKey = Objects.requireNonNull(entitiesByKey, "entitiesByKey");
        this.placedOn = new IdentityHashMap<>();
        this.orderedEntities = computeOrderAndParents();
    }

    @Override
    public @NotNull String modelId() {
        return modelId;
    }

    @Override
    public @NotNull Collection<RenderEntity> entities() {
        return java.util.Collections.unmodifiableCollection(entitiesByKey.values());
    }

    @Override
    public @Nullable RenderEntity entity(@NotNull String key) {
        Objects.requireNonNull(key, "key");
        return entitiesByKey.get(key);
    }

    @Override
    public void setBaseTransform(@NotNull Location location, @NotNull Quaternionf rotation) {
        if (closed) return;
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location.world is null");
        }
        baseLocation.setWorld(location.getWorld());
        baseLocation.setX(location.getX());
        baseLocation.setY(location.getY());
        baseLocation.setZ(location.getZ());
        baseLocation.setYaw(location.getYaw());
        baseLocation.setPitch(location.getPitch());
        baseRotation.set(rotation);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (closed) return;
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            entity.setEnabled(enabled);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void sync() {
        if (closed) return;
        if (baseLocation.getWorld() == null) {
            // If consumer forgot to set base transform, fall back to no-op to avoid NPE spam.
            return;
        }
        for (RenderEntityImpl entity : orderedEntities) {
            RenderEntityImpl parent = placedOn.get(entity);
            if (parent == null) {
                entity.setBaseTransformFast(baseLocation, baseRotation);
            } else {
                entity.setBaseTransformFast(parent.trackerLocation(), parent.trackerRotation());
            }
            entity.sync();
        }
    }

    void recomputeForSpawn() {
        if (closed) return;
        if (baseLocation.getWorld() == null) return;
        for (RenderEntityImpl entity : orderedEntities) {
            RenderEntityImpl parent = placedOn.get(entity);
            if (parent == null) {
                entity.setBaseTransformFast(baseLocation, baseRotation);
            } else {
                entity.setBaseTransformFast(parent.trackerLocation(), parent.trackerRotation());
            }
            entity.recomputeForSpawn();
        }
    }

    List<RenderEntityImpl> orderedEntitiesForSpawn() {
        return orderedEntities;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            entity.close();
        }
    }

    private List<RenderEntityImpl> computeOrderAndParents() {
        if (entitiesByKey.isEmpty()) {
            return Collections.emptyList();
        }

        // Resolve parent keys to entity instances (within the same model instance).
        Map<RenderEntityImpl, RenderEntityImpl> resolvedParent = new IdentityHashMap<>();
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            String parentKey = entity.placeOnKey();
            if (parentKey == null) continue;
            RenderEntityImpl parent = entitiesByKey.get(parentKey);
            if (parent == null) {
                InertiaLogger.warn("Render model '" + modelId + "': entity '" + entity.key() + "' place target '" + parentKey + "' not found");
                continue;
            }
            if (parent == entity) {
                InertiaLogger.warn("Render model '" + modelId + "': entity '" + entity.key() + "' cannot place on itself");
                continue;
            }
            resolvedParent.put(entity, parent);
        }

        // Build adjacency and indegrees.
        Map<RenderEntityImpl, Integer> indegree = new IdentityHashMap<>();
        Map<RenderEntityImpl, List<RenderEntityImpl>> children = new IdentityHashMap<>();
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            indegree.put(entity, 0);
            children.put(entity, new ArrayList<>());
        }
        for (Map.Entry<RenderEntityImpl, RenderEntityImpl> e : resolvedParent.entrySet()) {
            RenderEntityImpl child = e.getKey();
            RenderEntityImpl parent = e.getValue();
            children.get(parent).add(child);
            indegree.put(child, indegree.get(child) + 1);
        }

        ArrayDeque<RenderEntityImpl> queue = new ArrayDeque<>();
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            if (indegree.get(entity) == 0) {
                queue.add(entity);
            }
        }

        List<RenderEntityImpl> order = new ArrayList<>(entitiesByKey.size());
        while (!queue.isEmpty()) {
            RenderEntityImpl entity = queue.removeFirst();
            order.add(entity);
            for (RenderEntityImpl child : children.get(entity)) {
                int d = indegree.get(child) - 1;
                indegree.put(child, d);
                if (d == 0) {
                    queue.addLast(child);
                }
            }
        }

        if (order.size() != entitiesByKey.size()) {
            // Cycle (or multiple cycles): break by dropping parent links for remaining entities.
            Set<RenderEntityImpl> inOrder = Collections.newSetFromMap(new IdentityHashMap<>());
            inOrder.addAll(order);
            for (RenderEntityImpl entity : entitiesByKey.values()) {
                if (inOrder.contains(entity)) continue;
                InertiaLogger.warn("Render model '" + modelId + "': cyclic 'place' detected for entity '" + entity.key() + "', ignoring its place");
                resolvedParent.remove(entity);
                order.add(entity);
            }
        }

        placedOn.clear();
        placedOn.putAll(resolvedParent);
        return Collections.unmodifiableList(order);
    }
}
