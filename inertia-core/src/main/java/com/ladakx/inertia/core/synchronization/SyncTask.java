package com.ladakx.inertia.core.synchronization;

import com.ladakx.inertia.core.physics.PhysicsManager;
import com.ladakx.inertia.core.visualization.BodyVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;

/**
 * A BukkitRunnable responsible for synchronizing the state of physical bodies
 * from the physics thread to the main server thread.
 * This task reads the latest transformation data and updates the corresponding Minecraft entities.
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
        Map<Integer, PhysicsManager.BodyState> latestStates = physicsManager.getLatestBodyStates();
        if (latestStates.isEmpty()) {
            return;
        }

        for (PhysicsManager.BodyState state : latestStates.values()) {
            UUID entityId = visualizer.getEntityId(state.bodyId());
            if (entityId == null) {
                continue;
            }

            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                // TODO: Handle cleanup if the entity is no longer valid
                continue;
            }

            // Bukkit/Spigot doesn't have a direct rotation API for all entities.
            // Teleport is the most universal way to update both position and rotation.
            Location newLocation = state.position().toLocation(entity.getWorld());

            // Convert quaternion to yaw/pitch for Bukkit
            Quaternionf rotation = state.rotation();
            double[] euler = toEulerAngles(rotation);
            newLocation.setYaw((float) Math.toDegrees(euler[1])); // Yaw is around Y
            newLocation.setPitch((float) Math.toDegrees(euler[0])); // Pitch is around X

            // Perform the teleport on the main thread
            entity.teleport(newLocation);
        }
    }

    /**
     * Converts a quaternion to Euler angles (pitch, yaw, roll).
     * @param q The quaternion.
     * @return A double array containing [pitch, yaw, roll] in radians.
     */
    private double[] toEulerAngles(Quaternionf q) {
        double[] angles = new double[3];

        // Roll (x-axis rotation)
        double sinr_cosp = 2 * (q.w * q.x + q.y * q.z);
        double cosr_cosp = 1 - 2 * (q.x * q.x + q.y * q.y);
        angles[2] = Math.atan2(sinr_cosp, cosr_cosp);

        // Pitch (y-axis rotation)
        double sinp = 2 * (q.w * q.y - q.z * q.x);
        if (Math.abs(sinp) >= 1) {
            angles[0] = Math.copySign(Math.PI / 2, sinp); // use 90 degrees if out of range
        } else {
            angles[0] = Math.asin(sinp);
        }

        // Yaw (z-axis rotation)
        double siny_cosp = 2 * (q.w * q.z + q.x * q.y);
        double cosy_cosp = 1 - 2 * (q.y * q.y + q.z * q.z);
        angles[1] = Math.atan2(siny_cosp, cosy_cosp);

        return angles;
    }
}
