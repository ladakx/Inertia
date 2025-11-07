package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Vector3f;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 */
public class CompoundShape extends CompoundCollisionShape {

    public CompoundShape(BoundingBox box) {
        super();
        super.addChildShape(BoxShape.create(box), box.getCenter(new Vector3f()));
    }

    public CompoundShape(Iterable<BoundingBox> boxes) {
        super();
        for (BoundingBox box : boxes) {
            super.addChildShape(BoxShape.create(box), box.getCenter(new Vector3f()));
        }
    }

    public static CompoundShape create(BoundingBox box) {
        return new CompoundShape(box);
    }

    public static CompoundShape create(Iterable<BoundingBox> boxes) {
        return new CompoundShape(boxes);
    }
}
