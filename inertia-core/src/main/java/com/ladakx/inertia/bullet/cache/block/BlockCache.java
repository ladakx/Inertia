package com.ladakx.inertia.bullet.cache.block;

import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.bullet.block.BulletBlockData;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Used for storing block that can be queried during physics execution.
 * An implementation of this should be updated/reloaded every tick on the
 * main game thread.
 *
 * @see MinecraftSpace
 */
public interface BlockCache {

    MinecraftSpace getSpace();

    default void refreshBlockData(BlockPos blockPos, BlockState blockState) {}

    @Nullable
    BulletBlockData getBlockData(BlockPos blockPos);
}