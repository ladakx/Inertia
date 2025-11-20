package com.ladakx.inertia.utils.block;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Class representing a block position in a Minecraft world.
 */
public class BlockPos {
    private final int x;
    private final int y;
    private final int z;

    /**
     * Constructor to create a BlockPos with specified coordinates.
     * @param x The x-coordinate of the block position
     * @param y The y-coordinate of the block position
     * @param z The z-coordinate of the block position
     */
    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Constructor to create a BlockPos from a Bukkit Block.
     * @param block The block to use for creating the BlockPos
     */
    public BlockPos(Block block) {
        this(block.getX(), block.getY(), block.getZ());
    }

    /**
     * Get the Block at this BlockPos in a given world.
     * @param world The world to get the block from
     * @return The block at the BlockPos
     */
    public Block getBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    /**
     * Get the BlockState at this BlockPos in a given world.
     * @param world The world to get the block state from
     * @return The block state at the BlockPos
     */
    public BlockState getBlockState(World world) {
        return world.getBlockAt(x, y, z).getState();
    }

    /**
     * Get the bounding box of the block at this BlockPos using Jolt Physics.
     * @return The Jolt AABox of the block
     */
    public AaBox boundingBox() {
        // Створення Box 1x1x1 за координатами блоку
        return new AaBox(
                new Vec3((float) x, (float) y, (float) z),
                new Vec3((float) x + 1, (float) y + 1, (float) z + 1)
        );
    }

    /**
     * Get the x-coordinate of this BlockPos.
     * @return The x-coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Get the y-coordinate of this BlockPos.
     * @return The y-coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Get the z-coordinate of this BlockPos.
     * @return The z-coordinate
     */
    public int getZ() {
        return z;
    }
}