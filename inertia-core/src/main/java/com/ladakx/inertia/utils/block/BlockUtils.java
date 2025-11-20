//package com.ladakx.inertia.utils.block;
//
//import com.ladakx.inertia.InertiaPlugin;
//import com.ladakx.inertia.utils.Direction;
//import com.ladakx.inertia.nms.jolt.JoltNMSTools;
//import org.bukkit.Chunk;
//import org.bukkit.Material;
//import org.bukkit.block.BlockState;
//
///**
// * Utility class for working with blocks
// */
//public class BlockUtils {

//    /**
//     * Directions for checking surrounding blocks
//     */
//    public static final Direction[] DIRECTIONS = {
//            Direction.UP, Direction.DOWN,
//            Direction.NORTH, Direction.SOUTH,
//            Direction.WEST, Direction.EAST
//    };
//
//    /**
//     * Check if the block is rendered
//     * @param block The block to check
//     * @return true if the block is rendered
//     */
//    public static boolean isRendered(BlockState block) {
//        Chunk ch = block.getChunk();
//        if (block.getChunk().getChunkSnapshot().getBlockSkyLight(
//                block.getX()-(ch.getX()<<4), block.getY(), block.getZ()-(ch.getZ()<<4)
//        ) == 0) {
//            return !isSurroundedBySolidBlocks(block);
//        }
//
//        return true;
//    }
//
//    /**
//     * Check if the block is rendered
//     * @param block The block to check
//     * @param chunk The chunk the block is in
//     * @return true if the block is rendered
//     */
//    public static boolean isRendered(BlockState block, Chunk chunk) {
//        int sky = InertiaPlugin.getInstance().getJoltNMSTools().getBlockSkyLight(chunk, block.getX(), block.getY(), block.getZ());
//        if (sky == 0) {
//            return !isSurroundedBySolidBlocks(block, chunk);
//        }
//
//        return true;
//    }

//    /**
//     * Check if the block is surrounded by solid blocks
//     * @param block The block to check
//     * @return true if the block is surrounded by solid blocks
//     */
//    public static boolean isSurroundedBySolidBlocks(BlockState block) {
//        for (Direction dir : DIRECTIONS) {
//            if (!isCollidableFullBlock(
//                    block.getWorld()
//                            .getBlockState(block.getX() + dir.dx, block.getY() + dir.dy, block.getZ() + dir.dz)
//                            .getType()
//                ))
//            {
//                return false;
//            }
//        }
//
//        return true;
//    }

//    /**
//     * Check if the block is surrounded by solid blocks
//     * @param block The block to check
//     * @param chunk The chunk the block is in
//     * @return true if the block is surrounded by solid blocks
//     */
//    public static boolean isSurroundedBySolidBlocks(BlockState block, Chunk chunk) {
//        // Кешируем необходимые объекты и параметры один раз перед циклом
//        JoltNMSTools nms = InertiaPlugin.getInstance().getJoltNMSTools();
//        int chunkBlockX = chunk.getX() << 4;
//        int chunkBlockZ = chunk.getZ() << 4;
//        int minSectionY = nms.getMinSectionY(chunk);
//        int worldMinHeight = chunk.getWorld().getMinHeight();
//
//        // Текущие координаты блока в пределах чанка
//        int blockXWithinChunk = block.getX() - chunkBlockX;
//        int blockZWithinChunk = block.getZ() - chunkBlockZ;
//
//        for (Direction dir : DIRECTIONS) {
//            // Координаты для проверки
//            int offsetX = blockXWithinChunk + dir.dx;
//            int offsetZ = blockZWithinChunk + dir.dz;
//
//            // Если выходим за границы текущего чанка по X или Z
//            if (offsetX < 0 || offsetX >= 16 || offsetZ < 0 || offsetZ >= 16) {
//                // Берём блок из мира по глобальным координатам
//                int worldCheckX = block.getX() + dir.dx;
//                int worldCheckY = block.getY() + dir.dy;
//                int worldCheckZ = block.getZ() + dir.dz;
//
//                if (!isCollidableFullBlock(block.getWorld().getBlockState(worldCheckX, worldCheckY, worldCheckZ).getType()))
//                    return false;
//
//            } else {
//                // Считаем Y для секции
//                // смещение (block.getY() - worldMinHeight) — позиция внутри вертикального столбца
//                int secY = (block.getY() >> 4) - minSectionY;
//                int offsetY = (block.getY() - worldMinHeight) - (secY << 4) + dir.dy;
//
//                // Если мы уходим за границы текущей секции в Y,
//                // то смещаемся на секцию выше или ниже
//                if (offsetY > 15) {
//                    secY++;
//                    offsetY -= 16;
//                } else if (offsetY < 0) {
//                    secY--;
//                    offsetY += 16;
//                }
//
//                // Проверяем материал блока через NMS API
//                if (!isCollidableFullBlock(nms.getMaterial(chunk, secY, offsetX, offsetY, offsetZ))) {
//                    return false;
//                }
//            }
//        }
//
//        return true;
//    }


//    // Твердые блоки с твердым хитбоксом (которые не прозрачны (хитбокс 1х1х1) Чтобы небыло провалов в физике
//    // Используются для определения замурован ли соседний блок
//    private static final boolean[] COLLIDABLE_FULL_BLOCK = new boolean[Material.values().length];
//    // Твердые блоки с твердым хитбоксом
//    // Используются для определения может ли транспорт ездить в этом блоке
//    private static final boolean[] COLLIDABLE = new boolean[Material.values().length];
//
//    /**
//     * Initialize the collidable cache
//     */
//    public static void initCollidableCache() {
//        for (Material mat : Material.values()) {
//            BulletBlockSettings property = BulletBlockSettings.getBlockSettings(mat);
//
//            COLLIDABLE_FULL_BLOCK[mat.ordinal()] = mat.isBlock() && property.collidable() && property.isFullBlock();
//            COLLIDABLE[mat.ordinal()] = mat.isBlock() && property.collidable();
//        }
//    }
//
//    /**
//     * Check if the block is collidable (Has bounding box)
//     * @param type The material of the block
//     * @return true if the block is collidable
//     */
//    public static boolean isCollidable(Material type) {
//        return COLLIDABLE[type.ordinal()];
//    }
//
//    /**
//     * Check if the block is a full block and collidable
//     * @param type The material of the block
//     * @return true if the block is a full block and collidable
//     */
//    public static boolean isCollidableFullBlock(Material type) {
//        return COLLIDABLE_FULL_BLOCK[type.ordinal()];
//    }
//}
