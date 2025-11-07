package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 */
public class BoxShape extends BoxCollisionShape {

    public BoxShape(BoundingBox box) {
        super(box.getXExtent(), box.getYExtent(), box.getZExtent());
    }

    public static BoxShape create(BoundingBox box) {
        return new BoxShape(box);
    }
}
