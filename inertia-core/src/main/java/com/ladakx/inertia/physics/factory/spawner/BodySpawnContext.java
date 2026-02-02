package com.ladakx.inertia.physics.factory.spawner;

import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record BodySpawnContext(
        @NotNull PhysicsWorld world,
        @NotNull Location location,
        @NotNull String bodyId,
        @Nullable Player player,
        @NotNull Map<String, Object> params
) {
    public <T> T getParam(String key, Class<T> type, T defaultValue) {
        Object val = params.get(key);
        if (type.isInstance(val)) {
            return type.cast(val);
        }
        return defaultValue;
    }

    public <T> T getParam(String key, Class<T> type) {
        return getParam(key, type, null);
    }
}