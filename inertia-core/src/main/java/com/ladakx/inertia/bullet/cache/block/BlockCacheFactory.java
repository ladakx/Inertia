package com.ladakx.inertia.bullet.cache.block;

import com.ladakx.inertia.bullet.space.MinecraftSpace;

public interface BlockCacheFactory {
    BlockCache create(MinecraftSpace space);
}
