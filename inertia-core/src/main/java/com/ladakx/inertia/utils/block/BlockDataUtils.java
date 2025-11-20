//package com.ladakx.inertia.utils.block;
//
//import com.ladakx.inertia.bullet.block.BulletBlockData;
//import com.ladakx.inertia.bullet.shapes.BlockMeshShape;
//import org.bukkit.block.Block;
//import org.bukkit.block.BlockState;
//import org.bukkit.block.data.BlockData;
//import org.jetbrains.annotations.NotNull;
//
///**
// * Utility class for handling block data conversions and manipulations.
// */
//public class BlockDataUtils {
//
//    /**
//     * Get BulletBlockData from a Bukkit Block.
//     * @param block The Bukkit Block to convert
//     * @return The converted BulletBlockData
//     */
//    public static BulletBlockData getBulletBlockData(Block block) {
//        return getBulletBlockData(block.getState());
//    }
//
//    /**
//     * Get BulletBlockData from a Bukkit BlockState.
//     * @param block The Bukkit BlockState to convert
//     * @return The converted BulletBlockData
//     */
//    public static BulletBlockData getBulletBlockData(BlockState block) {
//        BlockMeshShape shape = BlockMeshShape.create(block);
//        return new BulletBlockData(block.getType(), block, shape);
//    }
//
//    /**
//     * Get a unique key representing the BlockState.
//     * @param block The BlockState to get the key for
//     * @return The generated BlockState key as a String
//     */
//    @NotNull
//    public static String getBlockStateKey(BlockState block) {
//        // Initialize an empty data string
//        String data = "";
//
//        // Check if the block is of type BlockData and get its string representation
//        if (block instanceof BlockData blockData) {
//            data = blockData.getAsString();
//        }
//
//        // Uncomment the following lines if you need to handle specific block data types such as Directional, Openable, Slab, Fence, or Wall
//        // BlockFace dir = BlockFace.SELF;
//        // if (block instanceof Directional) {
//        //     dir = ((Directional) block.getBlockData()).getFacing();
//        // }
//
//        // boolean isOpen = false, isHalf = false; Set<BlockFace> fenceFaces;
//        // if (block instanceof Openable openable) {
//        //     isOpen = openable.isOpen();
//        // } else if (block instanceof Slab slab) {
//        //     isHalf = slab.getType() == Slab.Type.BOTTOM;
//        // } else if (block instanceof Fence fence) {
//        //     fenceFaces = fence.getFaces();
//        // } else if (block instanceof Wall wall) {
//        //     fenceFaces = wall.getAsString();
//        // }
//
//        // Return the data string
//        return data;
//    }
//}