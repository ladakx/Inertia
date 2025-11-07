package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 */
public class CapsuleShape extends CapsuleCollisionShape {

    public CapsuleShape(BoundingBox box) {
        super(box.getXExtent(), box.getYExtent());
    }

    public static CapsuleShape create(BoundingBox box) {
        return new CapsuleShape(box);
    }
}
