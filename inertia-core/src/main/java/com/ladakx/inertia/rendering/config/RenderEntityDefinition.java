package com.ladakx.inertia.rendering.config;

import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Objects;

/**
 * Опис однієї локальної сутності.
 */
public record RenderEntityDefinition(
        String key,
        EntityKind kind,
        String itemModelKey,
        Material blockType,
        InertiaDisplayMode displayMode, // Абстракція
        Vector localOffset,
        Quaternionf localRotation,
        Vector scale,
        Vector translation,
        boolean showWhenActive,
        boolean showWhenSleeping,
        boolean rotTranslation,
        Float viewRange,
        Float shadowRadius,
        Float shadowStrength,
        Integer interpolationDuration,
        Integer teleportDuration,
        InertiaBillboard billboard,
        Integer brightnessBlock,
        Integer brightnessSky,
        boolean small,
        boolean invisible,
        boolean marker,
        boolean basePlate,
        boolean arms
) {

    public enum EntityKind {
        ARMOR_STAND,
        ITEM_DISPLAY,
        BLOCK_DISPLAY
    }

    public RenderEntityDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(localOffset, "localOffset");
        Objects.requireNonNull(localRotation, "localRotation");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(translation, "translation");
    }
}