package com.ladakx.inertia.bullet.cache.block.impl;

import com.ladakx.inertia.bullet.cache.block.BlockCacheFactory;
import com.ladakx.inertia.bullet.space.MinecraftSpace;

public class GuavaBlockCacheFactory implements BlockCacheFactory {

    @Override
    public GuavaBlockCache create(MinecraftSpace space) {
        return new GuavaBlockCache(space);
    }
}