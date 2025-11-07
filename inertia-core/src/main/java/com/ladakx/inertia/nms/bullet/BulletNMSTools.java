package com.ladakx.inertia.nms.bullet;

import com.jme3.bounding.BoundingBox;
import com.ladakx.inertia.enums.Direction;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.List;

/**
 * BulletNMSTools is an interface that defines various methods
 * for working with block states and bounding boxes in a custom NMS context.
 */
public interface BulletNMSTools {

    /**
     * Gets the unique integer identifier for the given block state.
     * This identifier can be used to compare different block states.
     *
     * @param blockState the state of the block to get the ID from
     * @return the unique block ID as an int
     */
    int getBlockId(BlockState blockState);

    /**
     * Checks if two block states are equal based on their block IDs.
     * This is useful for comparing blocks without comparing their full states.
     *
     * @param blockState1 the first block state to compare
     * @param blockState2 the second block state to compare
     * @return true if both block states have the same ID, otherwise false
     */
    boolean equalsById(BlockState blockState1, BlockState blockState2);

    /**
     * Calculates and returns the bounding box of the given block state.
     * The bounding box represents the collision or interaction area of the block.
     *
     * @param blockState the state of the block to get the bounding box for
     * @return the bounding box of the block state
     */
    BoundingBox boundingBox(BlockState blockState);

    /**
     * Calculates and returns a list of bounding boxes for the given block state.
     * This is useful for blocks with multiple collision areas.
     *
     * @param blockState the state of the block to get the bounding boxes for
     * @return a list of bounding boxes associated with the block state
     */
    List<BoundingBox> boundingBoxes(BlockState blockState);

    /**
     * Creates a block state from the given material.
     * This is useful to generate a block state that corresponds to a specific type of material.
     *
     * @param material the material to create the block state from
     * @return a new block state corresponding to the given material
     */
    BlockState createBlockState(Material material);

    /**
     * Retrieves the block state at the specified coordinates within a chunk.
     *
     * @param chunk the chunk where the block is located
     * @param x the x-coordinate of the block
     * @param y the y-coordinate of the block
     * @param z the z-coordinate of the block
     * @return the block state at the specified coordinates
     */
    BlockState getBlockState(Chunk chunk, int x, int y, int z);

    /**
     * Retrieves the occlusion shape (bounding box) of the block state at the specified coordinates within a chunk.
     * This shape is typically used for rendering or collision detection.
     *
     * @param chunk the chunk where the block is located
     * @param x the x-coordinate of the block
     * @param y the y-coordinate of the block
     * @param z the z-coordinate of the block
     * @return the bounding box representing the occlusion shape of the block state
     */
    BoundingBox getOcclusionShape(Chunk chunk, int x, int y, int z);

    /**
     * Determines whether a specific face of the block should be rendered.
     * This may depend on factors such as adjacent blocks or visibility in the world.
     *
     * @param world the world in which the block is located
     * @param block the block whose face is being rendered
     * @param face the direction of the face to check (e.g., North, South, etc.)
     * @return true if the face should be rendered, otherwise false
     */
    boolean renderFace(World world, Block block, Direction face);

    /**
     * Gets the number of sections in the chunk.
     * A section represents a vertical segment of the chunk.
     *
     * @param chunk the chunk to query
     * @return the total count of sections in the chunk
     */
    int getSectionsCount(Chunk chunk);

    /**
     * Retrieves the minimum Y level value of sections in the chunk.
     * This indicates the lowest vertical section available in the chunk.
     *
     * @param chunk the chunk to query
     * @return the minimum section Y level
     */
    int getMinSectionY(Chunk chunk);

    /**
     * Retrieves the maximum Y level value of sections in the chunk.
     * This indicates the highest vertical section available in the chunk.
     *
     * @param chunk the chunk to query
     * @return the maximum section Y level
     */
    int getMaxSectionY(Chunk chunk);

    /**
     * Determines if the specified section in the chunk contains only air blocks.
     *
     * @param chunk the chunk to query
     * @param numSect the section number to check
     * @return true if the section has only air blocks, otherwise false
     */
    boolean hasOnlyAir(Chunk chunk, int numSect);

    /**
     * Gets the count of non-empty blocks in the specified section of the chunk.
     * The result is typically returned as a short value.
     *
     * @param chunk the chunk to query
     * @param numSect the section number to check
     * @return the count of non-empty blocks in the section
     */
    short getSectionsNonEmptyBlocks(Chunk chunk, int numSect);

    /**
     * Determines if the specified section of the chunk is completely full.
     *
     * @param chunk the chunk to check
     * @param numSect the section number to evaluate
     * @return true if the section is full of blocks, otherwise false
     */
    boolean isSectionFull(Chunk chunk, int numSect);

    /**
     * Gets the material type of the block at the specified coordinates in the given section.
     *
     * @param chunk the chunk where the block is located
     * @param numSect the section number where the block is located
     * @param x the x-coordinate of the block within the section
     * @param y the y-coordinate of the block within the section
     * @param z the z-coordinate of the block within the section
     * @return the material of the block
     */
    Material getMaterial(Chunk chunk, int numSect, int x, int y, int z);

    /**
     * Retrieves the block state at the specified coordinates in the given section of the chunk.
     *
     * @param chunk the chunk where the block is located
     * @param numSect the section number where the block is located
     * @param x the x-coordinate of the block within the section
     * @param y the y-coordinate of the block within the section
     * @param z the z-coordinate of the block within the section
     * @return the block state of the specified block
     */
    BlockState getBlockState(Chunk chunk, int numSect, int x, int y, int z);

    /**
     * Retrieves the sky light level of the block at the specified coordinates in the chunk.
     * Sky light level represents the amount of light coming from the sky to the block.
     *
     * @param chunk the chunk where the block is located
     * @param x the x-coordinate of the block
     * @param y the y-coordinate of the block
     * @param z the z-coordinate of the block
     * @return the sky light level as an integer
     */
    int getBlockSkyLight(Chunk chunk, int x, int y, int z);

    /**
     * Retrieves the light level emitted by the block at the specified coordinates in the chunk.
     * This light level is due to the block's own properties (like torches, glowstone, etc.).
     *
     * @param chunk the chunk where the block is located
     * @param x the x-coordinate of the block
     * @param y the y-coordinate of the block
     * @param z the z-coordinate of the block
     * @return the emitted light level as an integer
     */
    int getBlockEmittedLight(Chunk chunk, int x, int y, int z);
}