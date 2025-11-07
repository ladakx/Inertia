package com.ladakx.inertia.nms.v1_16_r3.utils;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import net.minecraft.server.v1_16_R3.AxisAlignedBB;

public class BulletWrapUtils {

    public static BoundingBox convert(AxisAlignedBB aabb) {
        return new BoundingBox(
                new Vector3f((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ),
                new Vector3f((float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ)
        );
    }
}
