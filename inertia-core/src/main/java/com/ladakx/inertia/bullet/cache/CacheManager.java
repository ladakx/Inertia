package com.ladakx.inertia.bullet.cache;

import com.ladakx.inertia.bullet.cache.block.impl.GuavaBlockCacheFactory;

public class CacheManager {

    // ************************************
    // Cache Factory for Dynamic/Chunk Simulation
    private final GuavaBlockCacheFactory guavaBlockCacheFactory;

    public CacheManager() {
        this.guavaBlockCacheFactory = new GuavaBlockCacheFactory();
    }

    // ************************************
    // Getter Factor
    public GuavaBlockCacheFactory getGuavaBlockCacheFactory() {
        return guavaBlockCacheFactory;
    }
}
