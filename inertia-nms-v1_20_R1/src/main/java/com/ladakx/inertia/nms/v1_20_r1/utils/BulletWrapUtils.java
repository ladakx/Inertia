package com.ladakx.inertia.nms.v1_20_r1.utils;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.AABB;

public class BulletWrapUtils {

    public static BoundingBox convert(AABB aabb) {
        return new BoundingBox(
                new Vector3f((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ),
                new Vector3f((float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ)
        );
    }
}
