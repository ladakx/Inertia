package com.ladakx.inertia.nms.v1_16_r3.utils;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.server.v1_16_R3.AxisAlignedBB;

public class JoltWrapUtils {

    private JoltWrapUtils() {
        // utility class
    }

    public static AaBox convert(AxisAlignedBB aabb) {
        return new AaBox(
                new Vec3((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ),
                new Vec3((float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ)
        );
    }
}