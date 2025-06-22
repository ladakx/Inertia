/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/object/InertiaObjectManager.java */

package com.ladakx.inertia.core.object;

import com.ladakx.inertia.api.body.BodyType;
import com.ladakx.inertia.core.InertiaCore;
import com.ladakx.inertia.core.ntve.JNIBridge;
import org.bukkit.Location;
import org.joml.Quaternionf;

/**
 * A high-level manager for creating and destroying complete Inertia objects,
 * handling both their physical and visual aspects.
 */
public class InertiaObjectManager {

    private final InertiaCore core;

    public InertiaObjectManager(InertiaCore core) {
        this.core = core;
    }

    public void createDynamicBox(Location location) {
        // Schedule the creation on the physics thread
        core.getPhysicsEngine().scheduleCommand(() -> {
            // Create a default (non-rotated) quaternion.
            // Bukkit's Location doesn't provide a direct quaternion.
            Quaternionf rotation = new Quaternionf();

            long bodyId = JNIBridge.createBoxBody(
                    location.getX(), location.getY(), location.getZ(),
                    rotation.x, rotation.y, rotation.z, rotation.w,
                    BodyType.DYNAMIC.getInternalId(), // This will now compile correctly
                    0.5f, 0.5f, 0.5f // Half-extents for a 1x1x1 cube
            );

            if (bodyId != -1) {
                // Once the body is created, create its visual representation
                core.getBodyVisualManager().createVisualForBody(bodyId, location.getWorld());
            }
        });
    }
}