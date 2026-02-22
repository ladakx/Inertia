package com.ladakx.inertia.rendering;

import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

public interface NetworkVisual {

    int getId();

    /**
     * Creates the packet(s) required to spawn this visual.
     * Returns a single Packet or a BundlePacket.
     * * @param location The current location of the visual
     * @param rotation The current rotation (left_rotation) of the visual
     */
    Object createSpawnPacket(Location location, Quaternionf rotation);

    /**
     * Creates the packet required to destroy this visual.
     */
    Object createDestroyPacket();

    /**
     * Creates the packet required to update the position and rotation.
     */
    Object createTeleportPacket(Location location, Quaternionf rotation);

    /**
     * Creates a lightweight position-only packet for high-frequency movement updates.
     */
    default Object createPositionPacket(Location location, Quaternionf rotation) {
        return createTeleportPacket(location, rotation);
    }

    /**
     * Creates metadata packet containing transformation state (rotation / translation / scale).
     */
    default Object createTransformMetadataPacket(Quaternionf rotation) {
        return null;
    }

    /**
     * Creates the packet required to update metadata (flags, state, etc).
     */
    Object createMetadataPacket();

    void setGlowing(boolean glowing);

    // ---- Optional runtime mutators (best-effort, may be no-op) ----
    // Implementations may override these to support live updates without respawning the visual.

    /**
     * Sets the entity "invisible" flag in metadata.
     * Note: this is not the same as tracker-level enable/disable; see {@code VisualTracker.updateState(..., enabled)}.
     */
    default void setInvisible(boolean invisible) {}

    /**
     * Sets Display scale (for Display-based visuals only).
     */
    default void setScale(@Nullable Vector scale) {}

    /**
     * Sets Display translation (for Display-based visuals only).
     */
    default void setTranslation(@Nullable Vector translation) {}

    /**
     * Sets Display right rotation (for Display-based visuals only).
     * <p>
     * In config this is often called "local rotation"; it is applied after the left rotation.
     */
    default void setRightRotation(@Nullable Quaternionf rotation) {}

    /**
     * Sets Display billboard mode (for Display-based visuals only).
     */
    default void setBillboard(@Nullable InertiaBillboard billboard) {}

    /**
     * Sets Display view range (for Display-based visuals only).
     */
    default void setViewRange(@Nullable Float viewRange) {}

    /**
     * Sets Display shadow radius/strength (for Display-based visuals only).
     */
    default void setShadowRadius(@Nullable Float shadowRadius) {}
    default void setShadowStrength(@Nullable Float shadowStrength) {}

    /**
     * Sets Display interpolation/teleport duration (for Display-based visuals only).
     */
    default void setInterpolationDuration(@Nullable Integer interpolationDuration) {}
    default void setTeleportDuration(@Nullable Integer teleportDuration) {}
}
