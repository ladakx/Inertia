package com.ladakx.inertia.debug;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.shapes.util.TriangulatedBoundingBox;
import org.bukkit.*;
import java.util.ArrayList;
import java.util.List;

public class HitboxRender {

    /**
     * Main method that is called when rendering hitboxes.
     * You can call this synchronously or asynchronously based on your preference.
     *
     * @param rigidBody Your object that contains the RigidBody and shapes.
     * @param world The Minecraft World object where the particles will be spawned.
     */
    public static void render(PhysicsRigidBody rigidBody, World world) {
        Vector3f position = rigidBody.getMotionState().getLocation(new Vector3f());
        Quaternion orientation = rigidBody.getMotionState().getOrientation(new Quaternion());

        // List to collect bounding boxes from the collision shapes.
        List<BoundingBox> boxes = new ArrayList<>();

        // If the collision shape is a compound shape, retrieve bounding boxes for each child shape.
        if (rigidBody.getCollisionShape() instanceof CompoundCollisionShape compoundCollisionShape) {
            for (ChildCollisionShape child : compoundCollisionShape.listChildren()) {
                boxes.add(child.getShape().boundingBox(child.copyOffset(new Vector3f()), new Quaternion(), new BoundingBox()));
            }
        } else {
            // Otherwise, get a single bounding box from the collision shape.
            boxes.add(rigidBody.getCollisionShape().boundingBox(new Vector3f(), new Quaternion(), new BoundingBox()));
        }

        // Render each bounding box.
        for (BoundingBox box : boxes) {
            renderBox(box, position, orientation, world);
        }
    }

    /**
     * Synchronously renders a bounding box.
     *
     * @param box The bounding box to render.
     * @param position The position of the object.
     * @param orientation The orientation of the object.
     * @param world The Minecraft World object where the particles will be spawned.
     */
    public static void renderBox(BoundingBox box, Vector3f position, Quaternion orientation, World world) {
        // Get the minimum and maximum points of the bounding box.
        Vector3f min = box.getMin(new Vector3f());
        Vector3f max = box.getMax(new Vector3f());

        // Create a triangulated bounding box for rendering edges.
        TriangulatedBoundingBox bbox = new TriangulatedBoundingBox(min, max);
        List<Vector3f[]> triangles = bbox.getTriangles();

        // Process each triangle for edge rendering.
        for (Vector3f[] triangleVerts : triangles) {
            // Clone the vertices to avoid modifying original data.
            Vector3f v0 = triangleVerts[0].clone();
            Vector3f v1 = triangleVerts[1].clone();
            Vector3f v2 = triangleVerts[2].clone();

            // Apply rotation using the quaternion
            orientation.multLocal(v0);
            orientation.multLocal(v1);
            orientation.multLocal(v2);

            // Offset the vertices by the object's position
            v0.addLocal(position);
            v1.addLocal(position);
            v2.addLocal(position);

            // Finally, render the edges of the triangle.
            renderTriangleEdges(v0, v1, v2, world);
        }
    }

    /**
     * Renders the three edges of a triangle using particles and lines.
     *
     * @param v0 The first vertex of the triangle.
     * @param v1 The second vertex of the triangle.
     * @param v2 The third vertex of the triangle.
     * @param world The Minecraft World object where the particles will be spawned.
     */
    private static void renderTriangleEdges(Vector3f v0, Vector3f v1, Vector3f v2, World world) {
        // Render edges: from v0 to v1, v1 to v2, and v2 to v0.
        drawEdge(v0, v1, world);
        drawEdge(v1, v2, world);
        drawEdge(v2, v0, world);

        // Optionally spawn particles at the vertices.
        spawnParticle(world, v0, Color.RED);
        spawnParticle(world, v1, Color.RED);
        spawnParticle(world, v2, Color.RED);
    }

    /**
     * Draws a line between two points using particles.
     * The number of particles is controlled by configuration, e.g. hitboxParticleCount.
     *
     * @param start The start position of the edge.
     * @param end The end position of the edge.
     * @param world The Minecraft World object where the particles will be spawned.
     */
    private static void drawEdge(Vector3f start, Vector3f end, World world) {
        // Check if drawing lines is enabled.
        if (!InertiaPlugin.getPConfig().GENERAL.DEBUG.hitboxEnableLines) {
            return;
        }
        int count = InertiaPlugin.getPConfig().GENERAL.DEBUG.hitboxParticleCount;

        // Interpolate between the start and end point.
        for (int i = 1; i < count; i++) {
            float alpha = (float) i / count;
            Vector3f point = start.clone().lerp(end, alpha);
            spawnParticle(world, point, Color.YELLOW);
        }
    }

    /**
     * Spawns a single particle at the given position with the specified color.
     *
     * @param world The Minecraft World object where the particle will be spawned.
     * @param position The position at which to spawn the particle.
     * @param color The color of the particle.
     */
    private static void spawnParticle(World world, Vector3f position, Color color) {
        Location loc = new Location(world, position.x, position.y, position.z);
        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            loc.getWorld().spawnParticle(
                    Particle.DUST,  // Use DUST particle type
                    loc,
                    1,             // Spawn one particle
                    0, 0, 0,       // No extra offsets in x, y, z
                    new Particle.DustOptions(
                            color,
                            InertiaPlugin.getPConfig().GENERAL.DEBUG.hitboxParticleSize)  // Set particle size from configuration
            );
        });
    }
}