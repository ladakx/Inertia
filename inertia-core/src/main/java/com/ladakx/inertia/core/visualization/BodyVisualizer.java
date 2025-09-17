package com.ladakx.inertia.core.visualization;

import com.ladakx.inertia.api.body.InertiaBody;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the link between physical {@link InertiaBody} objects and their visible
 * representation as Minecraft {@link Entity} objects.
 */
public class BodyVisualizer {

    private final Map<Integer, UUID> bodyToEntityMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> entityToBodyMap = new ConcurrentHashMap<>();

    /**
     * Associates a physical body with a Minecraft entity for visualization.
     *
     * @param body   The InertiaBody to visualize.
     * @param entity The Minecraft entity that will represent the body.
     */
    public void startVisualizing(@NotNull InertiaBody body, @NotNull Entity entity) {
        bodyToEntityMap.put(body.getId(), entity.getUniqueId());
        entityToBodyMap.put(entity.getUniqueId(), body.getId());
    }

    /**
     * Stops visualizing a physical body, breaking the link to its Minecraft entity.
     *
     * @param body The InertiaBody to stop visualizing.
     */
    public void stopVisualizing(@NotNull InertiaBody body) {
        UUID entityId = bodyToEntityMap.remove(body.getId());
        if (entityId != null) {
            entityToBodyMap.remove(entityId);
        }
    }

    /**
     * Gets the UUID of the entity that represents the given body.
     *
     * @param bodyId The ID of the physical body.
     * @return The UUID of the associated entity, or {@code null} if not visualized.
     */
    @Nullable
    public UUID getEntityId(int bodyId) {
        return bodyToEntityMap.get(bodyId);
    }

    /**
     * Gets the ID of the body associated with the given entity UUID.
     *
     * @param entityId The UUID of the entity.
     * @return The ID of the associated body, or {@code null} if not tracked.
     */
    @Nullable
    public Integer getBodyId(@NotNull UUID entityId) {
        return entityToBodyMap.get(entityId);
    }

    /**
     * Clears all visualization mappings.
     */
    public void clear() {
        bodyToEntityMap.clear();
        entityToBodyMap.clear();
    }
}
