package com.ladakx.inertia.api.entity;

import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.UUID;

public interface IEntityPhysicsManager {
    void track(Entity entity);
    void untrack(UUID entityId);

    default void requireEntity(Entity entity) {
        Objects.requireNonNull(entity);
    }

    default void requireEntityId(UUID entityId) {
        Objects.requireNonNull(entityId);
    }
}
