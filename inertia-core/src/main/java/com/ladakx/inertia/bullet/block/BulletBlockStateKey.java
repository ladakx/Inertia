package com.ladakx.inertia.bullet.block;

import org.bukkit.block.BlockFace;

/**
 * Represents a key for a block state.
 * @param facing the facing of the block.
 * @param isOpen if the block is open.
 * @param isHalf if the block is half.
 */
public record BulletBlockStateKey(BlockFace facing, boolean isOpen, boolean isHalf) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BulletBlockStateKey that)) return false;
        return isHalf == that.isHalf && isOpen == that.isOpen && facing == that.facing;
    }

    @Override
    public String toString() {
        return "BulletBlockStateKey[" +
                "isHalf=" + isHalf + ", " +
                "facing=" + facing + ", " +
                "isOpen=" + isOpen + ']';
    }
}
