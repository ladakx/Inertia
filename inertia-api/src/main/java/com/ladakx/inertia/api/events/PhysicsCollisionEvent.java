package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается при столкновении двух физических тел.
 * ВНИМАНИЕ: Это событие вызывается АСИНХРОННО (из потока физики).
 * Не используйте методы Bukkit API, которые не потокобезопасны.
 */
public class PhysicsCollisionEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final PhysicsBody bodyA;
    private final PhysicsBody bodyB;
    private final Vector contactPoint;

    public PhysicsCollisionEvent(@NotNull PhysicsBody bodyA, @NotNull PhysicsBody bodyB, @NotNull Vector contactPoint) {
        super(true); // Async event
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.contactPoint = contactPoint;
    }

    @NotNull
    public PhysicsBody getBodyA() {
        return bodyA;
    }

    @NotNull
    public PhysicsBody getBodyB() {
        return bodyB;
    }

    @NotNull
    public Vector getContactPoint() {
        return contactPoint;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}