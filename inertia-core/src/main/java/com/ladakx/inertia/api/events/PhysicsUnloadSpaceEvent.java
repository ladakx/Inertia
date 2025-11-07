package com.ladakx.inertia.api.events;

import com.ladakx.inertia.bullet.space.MinecraftSpace;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PhysicsUnloadSpaceEvent extends Event {

    // HandlerList required by Bukkit for event handling.
    private static final HandlerList Handlers = new HandlerList();

    // The physics space in which the contact occurred.
    private final MinecraftSpace space;

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
     */
    public PhysicsUnloadSpaceEvent(MinecraftSpace space) {
        this.space = space;
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
