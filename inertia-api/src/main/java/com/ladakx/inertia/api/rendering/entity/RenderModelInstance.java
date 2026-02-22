package com.ladakx.inertia.api.rendering.entity;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Collection;

/**
 * A group of {@link RenderEntity} created from a {@code RenderModelDefinition}.
 * <p>
 * Base transform applies to all entities in the instance.
 */
public interface RenderModelInstance extends AutoCloseable {

    @NotNull String modelId();

    @NotNull Collection<RenderEntity> entities();

    @Nullable RenderEntity entity(@NotNull String key);

    void setBaseTransform(@NotNull Location location, @NotNull Quaternionf rotation);

    void setEnabled(boolean enabled);

    boolean isEnabled();

    default void show() {
        setEnabled(true);
        sync();
    }

    default void hide() {
        setEnabled(false);
        sync();
    }

    void sync();

    @Override
    void close();
}
