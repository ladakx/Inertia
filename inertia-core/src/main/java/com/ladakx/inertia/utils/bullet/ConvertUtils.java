package com.ladakx.inertia.utils.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.ladakx.inertia.enums.Direction;
import com.ladakx.inertia.utils.block.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Utility class for converting between Bullet and Bukkit
 */
public class ConvertUtils {

    /**
     * Convert a BlockPos to a Vector3f
     * @param blockPos The block position to convert
     * @return The converted Vector3f
     */
    public static Vector3f toBullet(BlockPos blockPos) {
        return new Vector3f(blockPos.getX() + 0.5f, blockPos.getY() + 0.5f, blockPos.getZ() + 0.5f);
    }

    /**
     * Convert a Vector3f to an EulerAngle
     * @param vector3f The vector to convert
     * @return The converted EulerAngle
     */
    public static EulerAngle toEulerAngle(Vector3f vector3f) {
        return new EulerAngle(vector3f.getX(), vector3f.getY(), vector3f.getZ());
    }

    /**
     * Convert a Vector3f to a Bukkit Vector
     * @param vector3f The vector to convert
     * @return The converted Vector
     */
    public static Vector toBukkit(Vector3f vector3f) {
        return new Vector(vector3f.getX(), vector3f.getY(), vector3f.getZ());
    }

    /**
     * Convert a Vector3f to a Bukkit Location
     * @param vector3f The vector to convert
     * @param world The world the location belongs to
     * @return The converted Location
     */
    public static Location toBukkitLoc(Vector3f vector3f, World world) {
        return new Location(world, vector3f.getX(), vector3f.getY(), vector3f.getZ());
    }

    /**
     * Convert an EulerAngle to a Vector3f
     * @param angle The EulerAngle to convert
     * @return The converted Vector3f
     */
    public static Vector3f toBullet(EulerAngle angle) {
        return new Vector3f((float) angle.getX(), (float) angle.getY(), (float) angle.getZ());
    }

    /**
     * Convert a Bukkit Vector to a Vector3f
     * @param vector The vector to convert
     * @return The converted Vector3f
     */
    public static Vector3f toBullet(Vector vector) {
        return new Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
    }

    /**
     * Convert a Bukkit Location to a Vector3f
     * @param location The location to convert
     * @return The converted Vector3f
     */
    public static Vector3f toBullet(Location location) {
        return new Vector3f((float) location.getX(), (float) location.getY(), (float) location.getZ());
    }

    /**
     * Convert a JOML Quaternionf to a JME Quaternion
     * @param rotation The quaternion to convert
     * @return The converted Quaternion
     */
    public static Quaternion toBullet(Quaternionf rotation) {
        return new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
    }

    /**
     * Convert a JME Quaternion to a JOML Quaternionf
     * @param rotation The quaternion to convert
     * @return The converted Quaternionf
     */
    public static Quaternionf toBukkit(Quaternion rotation) {
        return new Quaternionf(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());
    }

    /**
     * Convert a Direction enum to a Bukkit BlockFace enum
     * @param dir The direction to convert
     * @return The converted BlockFace
     */
    public static BlockFace toBukkit(Direction dir) {
        return BlockFace.valueOf(dir.name());
    }

    /**
     * Convert a Bukkit BlockFace enum to a Direction enum
     * @param dir The BlockFace to convert
     * @return The converted Direction
     */
    public static Direction toBullet(BlockFace dir) {
        return Direction.valueOf(dir.name());
    }

    /**
     * Convert an array of Direction enums to an array of Bukkit BlockFace enums
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
     * Convert an array of Bukkit BlockFace enums to an array of Direction enums
     * @param dirs The array of BlockFaces to convert
     * @return The converted array of Directions
     */
    public static Direction[] toBullet(BlockFace[] dirs) {
        Direction[] directions = new Direction[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            directions[i] = Direction.valueOf(dirs[i].name());
        }
        return directions;
    }
}