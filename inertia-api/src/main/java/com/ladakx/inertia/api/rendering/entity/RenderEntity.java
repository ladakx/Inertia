package com.ladakx.inertia.api.rendering.entity;

import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * A tracked visual entity with a base transform and a composable {@link TransformStack}.
 * <p>
 * Visibility is controlled via {@link #setEnabled(boolean)} without destroying the entity (it stays tracked).
 */
public interface RenderEntity extends AutoCloseable {

    @NotNull NetworkVisual visual();

    @NotNull TransformStack transforms();

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

    /**
     * Pushes the current state (transform + enabled flag) to the tracker.
     */
    void sync();

    /**
     * Requests a metadata update. When {@code critical=true}, forces a transform resync as well
     * (required for Display transform fields like scale/translation/right rotation).
     */
    void syncMetadata(boolean critical);

    // --- Convenience runtime setters (best-effort; may no-op on unsupported visuals) ---

    default void setGlowing(boolean glowing) {
        visual().setGlowing(glowing);
        syncMetadata(false);
    }

    default void setInvisible(boolean invisible) {
        visual().setInvisible(invisible);
        syncMetadata(false);
    }

    default void setScale(@Nullable Vector scale) {
        visual().setScale(scale);
        syncMetadata(true);
    }

    default void setTranslation(@Nullable Vector translation) {
        visual().setTranslation(translation);
        syncMetadata(true);
    }

    default void setRightRotation(@Nullable Quaternionf rotation) {
        visual().setRightRotation(rotation);
        syncMetadata(true);
    }

    default void setBillboard(@Nullable InertiaBillboard billboard) {
        visual().setBillboard(billboard);
        syncMetadata(false);
    }

    default void setViewRange(@Nullable Float viewRange) {
        visual().setViewRange(viewRange);
        syncMetadata(false);
    }

    default void setShadowRadius(@Nullable Float shadowRadius) {
        visual().setShadowRadius(shadowRadius);
        syncMetadata(false);
    }

    default void setShadowStrength(@Nullable Float shadowStrength) {
        visual().setShadowStrength(shadowStrength);
        syncMetadata(false);
    }

    @Override
    void close();
}
