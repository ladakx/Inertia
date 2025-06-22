/* New file: inertia-core/src/main/java/com/ladakx/inertia/core/visual/BodyVisualManager.java */

package com.ladakx.inertia.core.visual;

import com.ladakx.inertia.core.InertiaCore;
import com.ladakx.inertia.core.engine.PhysicsEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the visual representation (ItemDisplay entities) of physics bodies.
 * This class is responsible for creating, destroying, and synchronizing visuals.
 */
public class BodyVisualManager {

    private final InertiaCore core;
    private final Map<Long, ItemDisplay> visualMap = new ConcurrentHashMap<>();

    public BodyVisualManager(InertiaCore core) {
        this.core = core;
    }

    public void startSyncTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // This task runs on the main server thread
                for (Map.Entry<Long, ItemDisplay> entry : visualMap.entrySet()) {
                    long bodyId = entry.getKey();
                    ItemDisplay display = entry.getValue();

                    if (!display.isValid()) {
                        visualMap.remove(bodyId);
                        continue;
                    }

                    PhysicsEngine.PhysicsTransform transform = core.getPhysicsEngine().getTransform(bodyId);
                    if (transform != null) {
                        // We must teleport the entity to its new location
                        Location newLocation = new Location(display.getWorld(), transform.position().x, transform.position().y, transform.position().z);

                        // ItemDisplay requires a Transformation object to be updated
                        Transformation transformation = display.getTransformation();
                        transformation.getTranslation().set(0, 0, 0); // Translation is handled by teleport
                        transformation.getRightRotation().set(transform.rotation());

                        display.teleport(newLocation);
                        display.setTransformation(transformation);
                    }
                }
            }
        }.runTaskTimer(core.getPlugin(), 0L, 1L); // Run every server tick
    }

    public void createVisualForBody(long bodyId, World world) {
        // Run on main thread as we are interacting with Bukkit API
        core.getPlugin().getServer().getScheduler().runTask(core.getPlugin(), () -> {
            ItemDisplay display = world.spawn(new Location(world, 0, 0, 0), ItemDisplay.class, (d) -> {
                d.setItemStack(new ItemStack(Material.STONE));
                d.setBrightness(new Display.Brightness(15, 15)); // Make it fully bright

                // Initialize transformation
                Transformation transformation = d.getTransformation();
                transformation.getScale().set(new Vector3f(1.0f, 1.0f, 1.0f));
                d.setTransformation(transformation);
            });
            visualMap.put(bodyId, display);
        });
    }

    public void destroyVisualForBody(long bodyId) {
        ItemDisplay display = visualMap.remove(bodyId);
        if (display != null) {
            core.getPlugin().getServer().getScheduler().runTask(core.getPlugin(), display::remove);
        }
    }
}