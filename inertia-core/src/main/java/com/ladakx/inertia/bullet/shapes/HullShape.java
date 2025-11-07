package com.ladakx.inertia.bullet.shapes;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.ladakx.inertia.bullet.shapes.util.TriangulatedBoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Minecraft port shapes from JBullet
 * https://stephengold.github.io/Minie/minie/minie-library-tutorials/shape.html
 */
public class HullShape extends HullCollisionShape {
    public HullShape(List<TriangulatedBoundingBox> triangles) {
        super(triangles.stream()
                .flatMap(tbb -> tbb.getTriangles().stream())
                .flatMap(Arrays::stream)
                .collect(Collectors.toList()));
    }

    public static HullShape create(BoundingBox box) {
        return new HullShape(List.of(new TriangulatedBoundingBox(box)));
    }

    public static HullShape create(Iterable<BoundingBox> boxes) {
        List<TriangulatedBoundingBox> triangles = new ArrayList<>();
        for (BoundingBox box : boxes) {
            triangles.add(new TriangulatedBoundingBox(box));
        }

        return new HullShape(triangles);
    }
}
