package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PhysicsBodyDestroyEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final PhysicsBody body;

    public PhysicsBodyDestroyEvent(@NotNull PhysicsBody body) {
        this.body = body;
    }

    @NotNull
    public PhysicsBody getBody() {
        return body;
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