package com.ladakx.inertia.physics.debug;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EShapeType;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstBody;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.ConstSubShape;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ShapeDrawer {

    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 0.5f);
    private static final Particle.DustOptions BLUE_DUST = new Particle.DustOptions(Color.BLUE, 0.5f);
    private static final Particle.DustOptions SLEEPING_DUST = new Particle.DustOptions(Color.GRAY, 0.5f);

    public record Line(Vector3f start, Vector3f end) {}

    public static void drawBody(World world, ConstBody body, RVec3 origin) {
        if (body == null) return;

        Particle.DustOptions color;
        if (!body.isActive()) {
            color = SLEEPING_DUST;
        } else if (body.getMotionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Static) {
            color = RED_DUST;
        } else {
            color = BLUE_DUST;
        }

        List<Line> lines = getBodyLines(body, origin);
        for (Line line : lines) {
            drawParticleLine(world, line.start, line.end, color);
        }
    }

    public static List<Line> getBodyLines(ConstBody body, RVec3 origin) {
        RVec3 bodyPos = body.getPosition();
        Quat bodyRot = body.getRotation();

        double relX = bodyPos.xx() + origin.xx();
        double relY = bodyPos.yy() + origin.yy();
        double relZ = bodyPos.zz() + origin.zz();

        Matrix4f transform = new Matrix4f()
                .translate((float) relX, (float) relY, (float) relZ)
                .rotate(ConvertUtils.toJOML(bodyRot));

        List<Line> lines = new ArrayList<>();
        collectShapeLines(body.getShape(), transform, lines);
        return lines;
    }

    private static void collectShapeLines(ConstShape shape, Matrix4f transform, List<Line> lines) {
        EShapeType type = shape.getType();
        
        if (type == EShapeType.Compound) {
            CompoundShape compound = (CompoundShape) shape;
            int numSubShapes = compound.getNumSubShapes();
            for (int i = 0; i < numSubShapes; i++) {
                ConstSubShape subShape = compound.getSubShape(i);
                Vec3 pos = subShape.getPositionCom();
                Quat rot = subShape.getRotation();

                Matrix4f childTransform = new Matrix4f(transform);
                childTransform.translate(pos.getX(), pos.getY(), pos.getZ());
                childTransform.rotate(ConvertUtils.toJOML(rot));

                collectShapeLines(subShape.getShape(), childTransform, lines);
            }
        } else if (type == EShapeType.Decorated) {
             collectShapeLines(((DecoratedShape) shape).getInnerShape(), transform, lines);
        } else {
            // For convex and others, use OBB
            collectOrientedBoxLines(shape, transform, lines);
        }
    }

    private static void collectOrientedBoxLines(ConstShape shape, Matrix4f transform, List<Line> lines) {
        ConstAaBox localBounds = shape.getLocalBounds();
        Vec3 min = localBounds.getMin();
        Vec3 max = localBounds.getMax();

        Vector3f[] corners = new Vector3f[8];
        corners[0] = new Vector3f(min.getX(), min.getY(), min.getZ());
        corners[1] = new Vector3f(max.getX(), min.getY(), min.getZ());
        corners[2] = new Vector3f(min.getX(), max.getY(), min.getZ());
        corners[3] = new Vector3f(max.getX(), max.getY(), min.getZ());
        corners[4] = new Vector3f(min.getX(), min.getY(), max.getZ());
        corners[5] = new Vector3f(max.getX(), min.getY(), max.getZ());
        corners[6] = new Vector3f(min.getX(), max.getY(), max.getZ());
        corners[7] = new Vector3f(max.getX(), max.getY(), max.getZ());

        for (Vector3f v : corners) {
            transform.transformPosition(v);
        }

        // Bottom
        lines.add(new Line(corners[0], corners[1]));
        lines.add(new Line(corners[1], corners[3]));
        lines.add(new Line(corners[3], corners[2]));
        lines.add(new Line(corners[2], corners[0]));

        // Top
        lines.add(new Line(corners[4], corners[5]));
        lines.add(new Line(corners[5], corners[7]));
        lines.add(new Line(corners[7], corners[6]));
        lines.add(new Line(corners[6], corners[4]));

        // Sides
        lines.add(new Line(corners[0], corners[4]));
        lines.add(new Line(corners[1], corners[5]));
        lines.add(new Line(corners[2], corners[6]));
        lines.add(new Line(corners[3], corners[7]));
    }

    private static void drawParticleLine(World world, Vector3f start, Vector3f end, Particle.DustOptions color) {
        double dist = start.distance(end);
        if (dist == 0) return;
        
        double step = 0.5;
        double steps = Math.max(1, dist / step);
        
        double dx = (end.x - start.x) / steps;
        double dy = (end.y - start.y) / steps;
        double dz = (end.z - start.z) / steps;

        double cx = start.x;
        double cy = start.y;
        double cz = start.z;

        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.REDSTONE, cx, cy, cz, 1, 0, 0, 0, 0, color, true);
            cx += dx;
            cy += dy;
            cz += dz;
        }
    }
}