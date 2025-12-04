package com.ladakx.inertia.utils.jolt;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.ladakx.inertia.enums.Direction;
import com.ladakx.inertia.utils.block.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Utility class for converting between Jolt-JNI and Bukkit
 */
public final class ConvertUtils {

    private ConvertUtils() {
        // utility class
    }

    /**
     * Convert a BlockPos to a Jolt Vec3 (block center).
     *
     * @param blockPos The block position to convert
     * @return The converted Vec3
     */
    public static Vec3 toVec3(BlockPos blockPos) {
        return new Vec3(
                blockPos.getX() + 0.5f,
                blockPos.getY() + 0.5f,
                blockPos.getZ() + 0.5f
        );
    }

    public static RVec3 toRVec3(BlockPos blockPos) {
        return new RVec3(
                blockPos.getX() + 0.5f,
                blockPos.getY() + 0.5f,
                blockPos.getZ() + 0.5f
        );
    }

    /**
     * Convert a Jolt Vec3 to a Bukkit EulerAngle
     * (interpreting the components as XYZ rotations in radians).
     *
     * @param vec The vector to convert
     * @return The converted EulerAngle
     */
    public static EulerAngle toEulerAngle(Vec3 vec) {
        return new EulerAngle(vec.getX(), vec.getY(), vec.getZ());
    }

    public static EulerAngle toEulerAngle(RVec3 vec) {
        return new EulerAngle(vec.xx(), vec.yy(), vec.zz());
    }

    /**
     * Convert a Jolt Vec3 to a Bukkit Vector.
     *
     * @param vec The vector to convert
     * @return The converted Vector
     */
    public static Vector toBukkit(Vec3 vec) {
        return new Vector(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vector toBukkit(RVec3 vec) {
        return new Vector(vec.xx(), vec.yy(), vec.zz());
    }

    /**
     * Convert a Jolt Vec3 to a Bukkit Vector.
     *
     * @param vec The vector to convert
     * @return The converted Vector
     */
    public static Vector3f toJOML(Vec3 vec) {
        return new Vector3f(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vector3f toJOML(RVec3 vec) {
        return new Vector3f((float) vec.xx(), (float) vec.yy(), (float) vec.zz());
    }

    /**
     * Convert a Jolt Vec3 to a Bukkit Location.
     *
     * @param vec   The vector to convert
     * @param world The world the location belongs to
     * @return The converted Location
     */
    public static Location toBukkitLoc(Vec3 vec, World world) {
        return new Location(world, vec.getX(), vec.getY(), vec.getZ());
    }

    public static Location toBukkitLoc(RVec3 vec, World world) {
        return new Location(world, vec.xx(), vec.yy(), vec.zz());
    }

    /**
     * Convert a Bukkit EulerAngle to a Jolt Vec3
     * (XYZ rotations in radians).
     *
     * @param angle The EulerAngle to convert
     * @return The converted Vec3
     */
    public static Vec3 toVec3(EulerAngle angle) {
        return new Vec3(
                (float) angle.getX(),
                (float) angle.getY(),
                (float) angle.getZ()
        );
    }

    public static RVec3 toRVec3(EulerAngle angle) {
        return new RVec3(
                angle.getX(),
                angle.getY(),
                angle.getZ()
        );
    }

    /**
     * Convert a Bukkit Vector to a Jolt Vec3.
     *
     * @param vector The vector to convert
     * @return The converted Vec3
     */
    public static Vec3 toVec3(Vector vector) {
        return new Vec3(
                (float) vector.getX(),
                (float) vector.getY(),
                (float) vector.getZ()
        );
    }

    public static RVec3 toRVec3(Vector vector) {
        return new RVec3(
                vector.getX(),
                vector.getY(),
                vector.getZ()
        );
    }

    /**
     * Convert a Bukkit Location to a Jolt Vec3.
     *
     * @param location The location to convert
     * @return The converted Vec3
     */
    public static Vec3 toVec3(Location location) {
        return new Vec3(
                (float) location.getX(),
                (float) location.getY(),
                (float) location.getZ()
        );
    }

    public static RVec3 toRVec3(Location location) {
        return new RVec3(
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    /**
     * Convert a JOML Quaternionf to a Jolt Quat.
     *
     * @param rotation The quaternion to convert
     * @return The converted Quat
     */
    public static Quat toJolt(Quaternionf rotation) {
        return new Quat(
                rotation.x,
                rotation.y,
                rotation.z,
                rotation.w
        );
    }

    public static Quaternionf toJOML(Quat rotation) {
        return new Quaternionf(
                rotation.getX(),
                rotation.getY(),
                rotation.getZ(),
                rotation.getW()
        );
    }

    /**
     * Convert a Jolt Quat to a JOML Quaternionf.
     *
     * @param rotation The quaternion to convert
     * @return The converted Quaternionf
     */
    public static Quaternionf toBukkit(Quat rotation) {
        return new Quaternionf(
                rotation.getX(),
                rotation.getY(),
                rotation.getZ(),
                rotation.getW()
        );
    }

    /**
     * Convert a Direction enum to a Bukkit BlockFace enum.
     *
     * @param dir The direction to convert
     * @return The converted BlockFace
     */
    public static BlockFace toBukkit(Direction dir) {
        return BlockFace.valueOf(dir.name());
    }

    /**
     * Convert a Bukkit BlockFace enum to a Direction enum.
     *
     * @param dir The BlockFace to convert
     * @return The converted Direction
     */
    public static Direction toDirection(BlockFace dir) {
        return Direction.valueOf(dir.name());
    }

    /**
     * Convert an array of Direction enums to an array of Bukkit BlockFace enums.
     *
     * @param dirs The array of directions to convert
     * @return The converted array of BlockFaces
     */
    public static BlockFace[] toBukkit(Direction[] dirs) {
        BlockFace[] blockFaces = new BlockFace[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            blockFaces[i] = BlockFace.valueOf(dirs[i].name());
        }
        return blockFaces;
    }

    /**
     * Convert an array of Bukkit BlockFace enums to an array of Direction enums.
     *
     * @param dirs The array of BlockFaces to convert
     * @return The converted array of Directions
     */
    public static Direction[] toDirection(BlockFace[] dirs) {
        Direction[] directions = new Direction[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            directions[i] = Direction.valueOf(dirs[i].name());
        }
        return directions;
    }
}