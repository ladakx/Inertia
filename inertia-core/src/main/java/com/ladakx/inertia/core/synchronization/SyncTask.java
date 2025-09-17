package com.ladakx.inertia.core.synchronization;

import com.ladakx.inertia.core.physics.PhysicsManager;
import com.ladakx.inertia.core.visualization.BodyVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * A BukkitRunnable task that runs every server tick to synchronize the state of visible Minecraft entities
 * with their corresponding physical bodies from the Jolt simulation.
 */
public class SyncTask extends BukkitRunnable {

    private final PhysicsManager physicsManager;
    private final BodyVisualizer visualizer;

    public SyncTask(PhysicsManager physicsManager, BodyVisualizer visualizer) {
        this.physicsManager = physicsManager;
        this.visualizer = visualizer;
    }

    @Override
    public void run() {
        // Get the latest snapshot of body states from the physics thread's buffer.
        // This is a thread-safe operation.
        Map<Integer, PhysicsManager.BodyState> states = physicsManager.getLatestBodyStates();
        if (states.isEmpty()) {
            return;
        }

        // Get the map of visualized entities.
        Map<Integer, Entity> visualizedEntities = visualizer.getVisualizedBodies();

        for (PhysicsManager.BodyState state : states.values()) {
            Entity entity = visualizedEntities.get(state.bodyId());

            if (entity != null && entity.isValid()) {
                // We must use a synchronous teleport call from the main server thread.
                Location newLocation = new Location(
                        entity.getWorld(),
                        state.position().getX(),
                        state.position().getY(),
                        state.position().getZ()
                );

                // Note: Bukkit doesn't support direct quaternion rotation for most entities.
                // ArmorStand is an exception, but for general entities, we might need to convert
                // quaternion to yaw/pitch in the future if needed.
                // For now, teleporting is sufficient to see the movement.

                entity.teleport(newLocation);
            }
        }
    }
}

