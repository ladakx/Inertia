package com.ladakx.inertia.bullet.block;

import com.ladakx.inertia.bullet.shapes.BlockMeshShape;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a block in the physics world.
 * @param material the material of the block.
 * @param blockState the block state of the block.
 * @param shape the shape of the block.
 */
public record BulletBlockData(Material material, BlockState blockState, @Nullable BlockMeshShape shape) {

    @Override
    public String toString() {
        return "BulletBlockData[" +
                "block=" + material + ", " +
                "blockState=" + blockState + ", " +
                "shape=" + shape + ']';
    }
}