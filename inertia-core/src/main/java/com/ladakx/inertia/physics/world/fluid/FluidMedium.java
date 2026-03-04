package com.ladakx.inertia.physics.world.fluid;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

enum FluidMedium {
    WATER((byte) 0),
    LAVA((byte) 1);

    final byte id;

    FluidMedium(byte id) {
        this.id = id;
    }

    static @Nullable FluidMedium fromLiquidMaterial(Material material) {
        if (material == Material.LAVA) {
            return LAVA;
        }
        if (material == Material.WATER) {
            return WATER;
        }
        return null;
    }

    static FluidMedium fromId(byte id) {
        return id == LAVA.id ? LAVA : WATER;
    }
}
