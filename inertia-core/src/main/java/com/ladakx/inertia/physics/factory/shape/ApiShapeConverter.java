package com.ladakx.inertia.physics.factory.shape;

import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.OffsetCenterOfMassShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.TaperedCapsuleShapeSettings;
import com.github.stephengold.joltjni.TaperedCylinderShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.physics.ConvexHullShape;
import com.ladakx.inertia.api.physics.CompoundShape;
import com.ladakx.inertia.api.physics.CustomShape;
import com.ladakx.inertia.api.physics.PhysicsShape;
import com.ladakx.inertia.api.physics.ShapeInstance;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class ApiShapeConverter {

    public ShapeRefC toJolt(@NotNull PhysicsShape shape) {
        Objects.requireNonNull(shape, "shape");
        ConstShape raw = createRaw(shape);
        return raw.toRefC();
    }

    private ConstShape createRaw(PhysicsShape shape) {
        return switch (shape.kind()) {
            case BOX -> box((com.ladakx.inertia.api.physics.BoxShape) shape);
            case SPHERE -> sphere((com.ladakx.inertia.api.physics.SphereShape) shape);
            case CAPSULE -> capsule((com.ladakx.inertia.api.physics.CapsuleShape) shape);
            case CYLINDER -> cylinder((com.ladakx.inertia.api.physics.CylinderShape) shape);
            case TAPERED_CAPSULE -> taperedCapsule((com.ladakx.inertia.api.physics.TaperedCapsuleShape) shape);
            case TAPERED_CYLINDER -> taperedCylinder((com.ladakx.inertia.api.physics.TaperedCylinderShape) shape);
            case CONVEX_HULL -> convexHull((ConvexHullShape) shape);
            case COMPOUND -> compound((CompoundShape) shape);
            case CUSTOM -> custom((CustomShape) shape);
        };
    }

    private ConstShape custom(CustomShape shape) {
        throw new IllegalArgumentException("CustomShape(type=" + shape.type() + ") is not supported by this engine version");
    }

    private ConstShape box(com.ladakx.inertia.api.physics.BoxShape shape) {
        Vec3 halfExtents = new Vec3(shape.halfX(), shape.halfY(), shape.halfZ());
        return (shape.convexRadius() > 0)
                ? new com.github.stephengold.joltjni.BoxShape(halfExtents, shape.convexRadius())
                : new com.github.stephengold.joltjni.BoxShape(halfExtents);
    }

    private ConstShape sphere(com.ladakx.inertia.api.physics.SphereShape shape) {
        return new com.github.stephengold.joltjni.SphereShape(shape.radius());
    }

    private ConstShape capsule(com.ladakx.inertia.api.physics.CapsuleShape shape) {
        return new com.github.stephengold.joltjni.CapsuleShape(shape.height() * 0.5f, shape.radius());
    }

    private ConstShape cylinder(com.ladakx.inertia.api.physics.CylinderShape shape) {
        float halfHeight = shape.height() * 0.5f;
        return (shape.convexRadius() > 0)
                ? new com.github.stephengold.joltjni.CylinderShape(halfHeight, shape.radius(), shape.convexRadius())
                : new com.github.stephengold.joltjni.CylinderShape(halfHeight, shape.radius());
    }

    private ConstShape taperedCapsule(com.ladakx.inertia.api.physics.TaperedCapsuleShape shape) {
        TaperedCapsuleShapeSettings settings = new TaperedCapsuleShapeSettings(shape.height() * 0.5f, shape.topRadius(), shape.bottomRadius());
        return require(settings.create(), "TaperedCapsule");
    }

    private ConstShape taperedCylinder(com.ladakx.inertia.api.physics.TaperedCylinderShape shape) {
        float halfHeight = shape.height() * 0.5f;
        TaperedCylinderShapeSettings settings = (shape.convexRadius() > 0)
                ? new TaperedCylinderShapeSettings(halfHeight, shape.topRadius(), shape.bottomRadius(), shape.convexRadius())
                : new TaperedCylinderShapeSettings(halfHeight, shape.topRadius(), shape.bottomRadius());
        return require(settings.create(), "TaperedCylinder");
    }

    private ConstShape convexHull(ConvexHullShape shape) {
        Collection<Vec3> points = new ArrayList<>(shape.points().size());
        for (Vector3f p : shape.points()) {
            if (p == null) continue;
            points.add(new Vec3(p.x, p.y, p.z));
        }
        ConvexHullShapeSettings settings = (shape.convexRadius() > 0)
                ? new ConvexHullShapeSettings(points, shape.convexRadius())
                : new ConvexHullShapeSettings(points);
        return require(settings.create(), "ConvexHull");
    }

    private ConstShape compound(CompoundShape shape) {
        StaticCompoundShapeSettings settings = new StaticCompoundShapeSettings();

        for (ShapeInstance child : shape.children()) {
            if (child == null) continue;

            ConstShape childRaw = createRaw(child.shape());
            if (child.centerOfMassOffset() != null) {
                Vector3f com = child.centerOfMassOffset();
                OffsetCenterOfMassShapeSettings ocom = new OffsetCenterOfMassShapeSettings(new Vec3(com.x, com.y, com.z), childRaw);
                ShapeResult res = ocom.create();
                if (!res.hasError()) {
                    childRaw = res.get();
                }
            }

            Vector3f pos = child.position();
            Quaternionf rot = child.rotation();
            settings.addShape(
                    new Vec3(pos.x, pos.y, pos.z),
                    new Quat(rot.x, rot.y, rot.z, rot.w),
                    childRaw
            );
        }

        ShapeResult result = settings.create();
        if (result.hasError()) {
            throw new IllegalStateException("Jolt Compound Error: " + result.getError());
        }
        return result.get();
    }

    private ConstShape require(ShapeResult result, String name) {
        if (result.hasError()) {
            throw new IllegalStateException("Failed to create " + name + ": " + result.getError());
        }
        return result.get();
    }
}
