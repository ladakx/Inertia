package com.ladakx.inertia.api.events;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * PhysicsContactEvent represents an event triggered when two physics objects (rigid bodies)
 * come into contact within the Minecraft physics space.
 */
public class PhysicsContactEvent extends Event {

    // HandlerList required by Bukkit for event handling.
    private static final HandlerList Handlers = new HandlerList();

    // The physics space in which the contact occurred.
    private final MinecraftSpace space;

    // The first physics object involved in the collision.
    private final PhysicsRigidBody objectA;

    // The second physics object involved in the collision.
    private final PhysicsRigidBody objectB;

    // A unique identifier for the contact point where the collision occurred.
    private final long contactPointId;

    /**
     * Returns the HandlerList for this event.
     * This method is required by all Bukkit events.
     *
     * @return the HandlerList for handling the event.
     */
    public static HandlerList getHandlerList() {
        return Handlers;
    }

    /**
     * Constructor for the PhysicsContactEvent.
     *
     * @param space the MinecraftSpace where the physics event occurred.
     * @param objectA the first physics rigid body involved.
     * @param objectB the second physics rigid body involved.
     * @param contactPointId a unique identifier for the contact point.
     */
    public PhysicsContactEvent(MinecraftSpace space, PhysicsRigidBody objectA, PhysicsRigidBody objectB, long contactPointId) {
        this.space = space;
        this.objectA = objectA;
        this.objectB = objectB;
        this.contactPointId = contactPointId;
    }

    /**
     * Retrieves the Minecraft space where the contact event occurred.
     *
     * @return the physics space.
     */
    public MinecraftSpace getSpace() {
        return space;
    }

    /**
     * Retrieves the first physics rigid body involved in the contact.
     *
     * @return the first physics object.
     */
    public PhysicsRigidBody getObjectA() {
        return objectA;
    }

    /**
     * Retrieves the second physics rigid body involved in the contact.
     *
     * @return the second physics object.
     */
    public PhysicsRigidBody getObjectB() {
        return objectB;
    }

    /**
     * Retrieves the unique contact point identifier for this collision.
     *
     * @return the contact point ID.
     */
    public long getContactPointId() {
        return contactPointId;
    }

    /**
     * Returns the HandlerList for this event instance.
     * This method is required by all Bukkit events.
     *
     * @return the HandlerList for this event.
     */
    @Override
    public HandlerList getHandlers() {
        return Handlers;
    }
}