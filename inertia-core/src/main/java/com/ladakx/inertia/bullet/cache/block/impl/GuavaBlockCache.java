package com.ladakx.inertia.bullet.cache.block.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.ladakx.inertia.bullet.cache.block.BlockCache;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.utils.block.BlockDataUtils;
import com.ladakx.inertia.bullet.block.BulletBlockData;
import org.bukkit.block.BlockState;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GuavaBlockCache implements BlockCache {

    private final MinecraftSpace space;
    private final LoadingCache<BlockPos, BulletBlockData> blockDataCache;

    public GuavaBlockCache(MinecraftSpace space) {
        this.space = space;

        this.blockDataCache =
                CacheBuilder.newBuilder()
                .expireAfterAccess(20, TimeUnit.SECONDS)
                .build(CacheLoader.from(this::loadBlockData));
    }

    @Override
    public MinecraftSpace getSpace() {
        return space;
    }

    @Override
    public void refreshBlockData(BlockPos blockPos, BlockState blockState) {
        this.blockDataCache.invalidate(blockPos);
    }

    private BulletBlockData loadBlockData(BlockPos blockPos) {
        return BlockDataUtils.getBulletBlockData(blockPos.getBlock(space.getWorld()));
    }

    @Override
    public BulletBlockData getBlockData(BlockPos blockPos) {
        try {
            return blockDataCache.get(blockPos);
        } catch (ExecutionException e) {
            return null;
        }
    }
}