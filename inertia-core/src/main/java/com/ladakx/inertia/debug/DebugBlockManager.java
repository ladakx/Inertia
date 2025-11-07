package com.ladakx.inertia.debug;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.bodies.debug.PhysicsDebugBlock;
import com.ladakx.inertia.bullet.shapes.CompoundShape;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.files.config.PluginCFG;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * DebugBlockManager is responsible for managing the creation and removal
 * of debug blocks. These blocks are used to visualize collision boxes in the game.
 */
public class DebugBlockManager {

    // List storing all debug block elements
    private final HashMap<MinecraftSpace, Set<PhysicsDebugBlock>> blockElements;
    private final Set<PhysicsDebugBlock> blockElementsList;
    // The collision shape for a debug block (a box with half extents of 0.5)
    /**
     * Constructor initializes the list of debug blocks and sets up the collision box.
     */
    public DebugBlockManager() {
        this.blockElements = new HashMap<>();
        this.blockElementsList = new HashSet<>();
    }

    /**
     * Generates debug blocks within a square area defined by the given center and radius.
     *
     * @param center The center location (fixed, e.g., 56.5, 20.5, 45.5)
     * @param radius The distance from the center to span in both X and Z directions
     */
    public void generateDebugBlocks(Location center, int radius, String block) {
        // Loop through the X and Z coordinates around the center.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Create a new target location for each block
                Location targetLocation = center.clone().add(x, 0.5f, z);
                createDebugBlock(targetLocation, block);
            }
        }
    }

    /**
     * Creates a debug block at the given fixed location.
     *
     * @param location The fixed location (e.g., 56.5, 20.5, 45.5)
     */
    public void createDebugBlock(Location location, String block) {
        var config = InertiaPlugin.getPConfig().GENERAL.DEBUG.debugBlocks.get(block);

        MinecraftSpace space = InertiaPlugin.getBulletManager().getSpaceManager().getSpace(location.getWorld());
        CollisionShape shape = CompoundShape.create(config.box);
        PhysicsRigidBody rigidBody = getRigidBody(location, shape, config);

        // Create a new debug block with the given location, physics space, and rigid body
        PhysicsDebugBlock element = new PhysicsDebugBlock(block, location, space, rigidBody);
        space.addPhysicsElement(element);
        addBlockElement(element);
    }

    private @NotNull PhysicsRigidBody getRigidBody(Location location, CollisionShape shape, PluginCFG.General.Debug.DebugBlock config) {
        PhysicsRigidBody rigidBody = new PhysicsRigidBody(shape, config.mass);
        rigidBody.setPhysicsLocation(new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ()));
        rigidBody.setPhysicsRotation(new Quaternion());
        rigidBody.setAngularDamping(config.angularDamping);
        rigidBody.setLinearDamping(config.linearDamping);
        //rigidBody.setFriction(config.friction);
        //rigidBody.setRestitution(config.restitution);
        rigidBody.setEnableSleep(true);
        return rigidBody;
    }

    /**
     * Clears all debug blocks by removing physics elements and associated displays.
     */
    public void clearDebugBlocks() {
        // Remove each physics element and its display (if it exists)
        for (MinecraftSpace space : blockElements.keySet()) {
            for (PhysicsDebugBlock element : blockElements.get(space)) {
                space.removePhysicsElement(element, true);
            }
        }

        // Clean up any remaining debug block entities from all worlds
        for (PhysicsDebugBlock element : blockElementsList) {
            Entity entity = element.getDisplay();
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }

        // Clear the internal list of debug blocks
        this.blockElements.clear();
        this.blockElementsList.clear();
    }

    /**
     * Adds a debug block to the internal list.
     *
     * @param element The debug block element to add.
     */
    private void addBlockElement(PhysicsDebugBlock element) {
        blockElementsList.add(element);
        MinecraftSpace world = element.getSpace();

        if (blockElements.containsKey(world)) {
            blockElements.get(world).add(element);
        } else {
            Set<PhysicsDebugBlock> set = new HashSet<>();
            set.add(element);
            blockElements.put(world, set);
        }
    }

    /**
     * Returns the list of current debug block elements.
     *
     * @return A list of PhysicsDebugBlock elements.
     */
    public Set<PhysicsDebugBlock> getBlockElements() {
        return blockElementsList;
    }
}