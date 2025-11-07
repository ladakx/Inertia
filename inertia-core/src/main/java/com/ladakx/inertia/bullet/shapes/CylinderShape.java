package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 *
 * int axis = 0->X 1->Y 2->Z;
 * X <->
 * Y ^
 * Z |
 */
public class CylinderShape extends CylinderCollisionShape {
    public CylinderShape(float radius, float width, int axis) {
        super(radius, width, axis);
    }

    public static CylinderShape create(BoundingBox box) {
        return new CylinderShape(box.getXExtent(), box.getYExtent(), 2);
    }

    public static CylinderShape create(float radius, float width, int axis) {
        return new CylinderShape(radius, width, axis);
    }
}
