package com.ladakx.inertia.nms.v1_21_r3.utils;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.phys.AABB;

public class JoltWrapUtils {

    public static AaBox convert(AABB aabb) {
        return new AaBox(
                new Vec3((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ),
                new Vec3((float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ)
        );
    }
}