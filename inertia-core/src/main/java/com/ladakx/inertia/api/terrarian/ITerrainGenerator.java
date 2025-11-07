package com.ladakx.inertia.api.terrarian;

import com.ladakx.inertia.utils.block.BlockPos;
import org.bukkit.block.BlockState;

/**
 * ITerrainGenerator defines the contract for terrain generation
 * functionality, including stepping through the generation process
 * and refreshing specific blocks.
 */
public interface ITerrainGenerator {

    /**
     * Advances the terrain generation process by one step.
     * This method can be called repeatedly to gradually generate or update terrain.
     */
    void step();

    /**
     * Refreshes or updates the block at the given position with the provided block state.
     *
     * @param blockPos the position of the block to refresh
     * @param blockState the new state to apply to the block
     */
    void refresh(BlockPos blockPos, BlockState blockState);
}