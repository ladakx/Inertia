package com.ladakx.inertia.physics.world.fluid;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

enum FluidMedium {
    WATER(
            (byte) 0,
            1.0f,   // buoyancy multiplier relative to gravity
            0.35f,  // linear damping strength
            0.04f   // angular damping strength
    ),
    LAVA(
            (byte) 1,
            1.35f,
            1.1f,
            0.12f
    );

    final byte id;
    final float buoyancy;
    final float linearDamping;
    final float angularDamping;

    FluidMedium(byte id, float buoyancy, float linearDamping, float angularDamping) {
        this.id = id;
        this.buoyancy = buoyancy;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
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

