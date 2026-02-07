package com.ladakx.inertia.common.utils;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.common.Direction;
import com.ladakx.inertia.common.block.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ConvertUtils {

    private ConvertUtils() {
    }

    // --- Optimized (Zero-Allocation) Methods ---

    public static void copyToBukkit(RVec3 src, Vector dest) {
        dest.setX(src.xx());
        dest.setY(src.yy());
        dest.setZ(src.zz());
    }

    public static void copyToBukkit(Vec3 src, Vector dest) {
        dest.setX(src.getX());
        dest.setY(src.getY());
        dest.setZ(src.getZ());
    }

    public static void copyToJOML(RVec3 src, Vector3f dest) {
        dest.set((float) src.xx(), (float) src.yy(), (float) src.zz());
    }

    public static void copyToJOML(Quat src, Quaternionf dest) {
        dest.set(src.getX(), src.getY(), src.getZ(), src.getW());
    }
    
    // --- Legacy / Convenience Methods ---

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

    public static EulerAngle toEulerAngle(Vec3 vec) {
        return new EulerAngle(vec.getX(), vec.getY(), vec.getZ());
    }

    public static EulerAngle toEulerAngle(RVec3 vec) {
        return new EulerAngle(vec.xx(), vec.yy(), vec.zz());
    }

    public static Vector toBukkit(Vec3 vec) {
        return new Vector(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vector toBukkit(RVec3 vec) {
        return new Vector(vec.xx(), vec.yy(), vec.zz());
    }

    public static Vector3f toJOML(Vec3 vec) {
        return new Vector3f(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vector3f toJOML(RVec3 vec) {
        return new Vector3f((float) vec.xx(), (float) vec.yy(), (float) vec.zz());
    }

    public static Location toBukkitLoc(Vec3 vec, World world) {
        return new Location(world, vec.getX(), vec.getY(), vec.getZ());
    }

    public static Location toBukkitLoc(RVec3 vec, World world) {
        return new Location(world, vec.xx(), vec.yy(), vec.zz());
    }

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

    public static Quaternionf toBukkit(Quat rotation) {
        return new Quaternionf(
                rotation.getX(),
                rotation.getY(),
                rotation.getZ(),
                rotation.getW()
        );
    }

    public static BlockFace toBukkit(Direction dir) {
        return BlockFace.valueOf(dir.name());
    }

    public static Direction toDirection(BlockFace dir) {
        return Direction.valueOf(dir.name());
    }
}