package com.ladakx.inertia.core.visualization;

import com.ladakx.inertia.api.body.InertiaBody;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the association between physical {@link InertiaBody} objects and their visible Minecraft {@link Entity} counterparts.
 */
public class BodyVisualizer {

    private final Map<Integer, Entity> visualizedBodies = new ConcurrentHashMap<>();

    /**
     * Associates a physical body with a Minecraft entity for visualization.
     * The SyncTask will use this mapping to update the entity's position and rotation.
     *
     * @param body   The InertiaBody to visualize.
     * @param entity The Minecraft entity that will represent the body in the world.
     */
    public void visualizeBody(InertiaBody body, Entity entity) {
        visualizedBodies.put(body.getId(), entity);
    }

    /**
     * Removes the visualization mapping for a given body ID.
     * This should be called when a body is removed from the physics world.
     *
     * @param bodyId The ID of the body to stop visualizing.
     */
    public void unvisualizeBody(int bodyId) {
        visualizedBodies.remove(bodyId);
    }

    /**
     * Gets the map of all currently visualized bodies.
     * The key is the body ID, and the value is the associated Minecraft entity.
     *
     * @return A map of visualized bodies.
     */
    public Map<Integer, Entity> getVisualizedBodies() {
        return visualizedBodies;
    }
}

