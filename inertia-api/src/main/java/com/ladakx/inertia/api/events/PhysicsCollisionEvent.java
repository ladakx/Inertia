package com.ladakx.inertia.api.events;

import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
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
    private final InertiaPhysicsBody bodyA;
    private final InertiaPhysicsBody bodyB;
    private final Vector contactPoint;

    public PhysicsCollisionEvent(@NotNull InertiaPhysicsBody bodyA, @NotNull InertiaPhysicsBody bodyB, @NotNull Vector contactPoint) {
        super(true); // Async event
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.contactPoint = contactPoint;
    }

    @NotNull
    public InertiaPhysicsBody getBodyA() {
        return bodyA;
    }

    @NotNull
    public InertiaPhysicsBody getBodyB() {
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