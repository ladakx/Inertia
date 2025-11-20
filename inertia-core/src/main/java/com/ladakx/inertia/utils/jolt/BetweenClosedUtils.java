package com.ladakx.inertia.utils.jolt;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.ladakx.inertia.utils.block.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility class for getting all the blocks between two points.
 */
public final class BetweenClosedUtils {

    private BetweenClosedUtils() {
        // utility class
    }

    /**
     * Get all the blocks between two points.
     *
     * @param box The box to get the blocks from.
     * @return The list of blocks between the two points.
     */
    public static List<BlockPos> betweenClosed(ConstAaBox box) {
        List<BlockPos> result = new ArrayList<>();
        betweenClosed(box, result);
        return result;
    }

    /**
     * Get all the blocks between two points.
     *
     * @param box    The box to get the blocks from.
     * @param result The list to add the blocks to.
     */
    public static void betweenClosed(ConstAaBox box, Collection<BlockPos> result) {
        // Jolt AaBox має getMin()/getMax(), які повертають Vec3 [4]
        Vec3 min = box.getMin();
        Vec3 max = box.getMax();

        betweenClosed(
                (int) min.getX(), (int) min.getY(), (int) min.getZ(),
                (int) max.getX(), (int) max.getY(), (int) max.getZ(),
                result
        );
    }

    /**
     * Get all the blocks between two points.
     *
     * @param startX The starting x coordinate.
     * @param startY The starting y coordinate.
     * @param startZ The starting z coordinate.
     * @param endX   The ending x coordinate (exclusive).
     * @param endY   The ending y coordinate (exclusive).
     * @param endZ   The ending z coordinate (exclusive).
     * @return The list of blocks between the two points.
     */
    public static List<BlockPos> betweenClosed(int startX, int startY, int startZ,
                                               int endX, int endY, int endZ) {
        List<BlockPos> result = new ArrayList<>();
        betweenClosed(startX, startY, startZ, endX, endY, endZ, result);
        return result;
    }

    /**
     * Get all the blocks between two points.
     *
     * @param startX The starting x coordinate.
     * @param startY The starting y coordinate.
     * @param startZ The starting z coordinate.
     * @param endX   The ending x coordinate (exclusive).
     * @param endY   The ending y coordinate (exclusive).
     * @param endZ   The ending z coordinate (exclusive).
     * @param result The list to add the blocks to.
     */
    public static void betweenClosed(int startX, int startY, int startZ,
                                     int endX, int endY, int endZ,
                                     Collection<BlockPos> result) {
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                for (int z = startZ; z < endZ; z++) {
                    result.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}