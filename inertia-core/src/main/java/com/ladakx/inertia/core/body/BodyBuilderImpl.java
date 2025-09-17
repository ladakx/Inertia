package com.ladakx.inertia.core.body;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.ConvexShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import com.ladakx.inertia.api.body.BodyBuilder;
import com.ladakx.inertia.api.body.InertiaBody;
import com.ladakx.inertia.api.shape.CubeShape;
import com.ladakx.inertia.api.shape.InertiaShape;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public class BodyBuilderImpl implements BodyBuilder {

    private final PhysicsManager physicsManager;

    private Location location = new Location(null, 0, 0, 0);
    private Quaternionf rotation = new Quaternionf();
    private InertiaShape inertiaShape = new CubeShape(1, 1, 1);
    private com.ladakx.inertia.api.body.MotionType motionType = com.ladakx.inertia.api.body.MotionType.STATIC;
    private double mass = 1.0;
    private float friction = 0.2f;
    private float restitution = 0.0f;
    private Vector initialVelocity = new Vector(0, 0, 0);

    public BodyBuilderImpl(PhysicsManager physicsManager) {
        this.physicsManager = physicsManager;
    }

    @Override
    public BodyBuilder location(Location location) {
        this.location = location;
        return this;
    }

    @Override
    public BodyBuilder rotation(Quaternionf rotation) {
        this.rotation = rotation;
        return this;
    }

    @Override
    public BodyBuilder shape(InertiaShape shape) {
        this.inertiaShape = shape;
        return this;
    }

    @Override
    public BodyBuilder motionType(com.ladakx.inertia.api.body.MotionType motionType) {
        this.motionType = motionType;
        return this;
    }

    @Override
    public BodyBuilder mass(double mass) {
        this.mass = mass;
        return this;
    }

    @Override
    public BodyBuilder friction(float friction) {
        this.friction = friction;
        return this;
    }

    @Override
    public BodyBuilder restitution(float restitution) {
        this.restitution = restitution;
        return this;
    }

    @Override
    public BodyBuilder initialVelocity(Vector velocity) {
        this.initialVelocity = velocity;
        return this;
    }

    @Override
    public InertiaBody build() {
        ShapeSettings shapeSettings = createJoltShapeSettings();
        if (shapeSettings == null) {
            throw new IllegalStateException("Unsupported shape type: " + inertiaShape.getClass().getName());
        }

        if (motionType == com.ladakx.inertia.api.body.MotionType.DYNAMIC && shapeSettings instanceof ConvexShapeSettings) {
            double volume = calculateVolume(inertiaShape);
            if (volume > 1e-6) { // Avoid division by zero for points/lines
                float density = (float) (mass / volume);
                ((ConvexShapeSettings) shapeSettings).setDensity(density);
            }
        }

        RVec3Arg pos = new RVec3((float) location.getX(), (float) location.getY(), (float) location.getZ());
        QuatArg rot = new Quat(rotation.x, rotation.y, rotation.z, rotation.w);

        short objectLayer = (short) (motionType == com.ladakx.inertia.api.body.MotionType.STATIC
                ? PhysicsManager.LAYER_NON_MOVING
                : PhysicsManager.LAYER_MOVING);

        EMotionType joltMotionType = EMotionType.values()[motionType.ordinal()];

        BodyCreationSettings settings = new BodyCreationSettings(shapeSettings, pos, rot, joltMotionType, objectLayer);
        settings.setFriction(this.friction);
        settings.setRestitution(this.restitution);

        if (joltMotionType == EMotionType.Dynamic) {
            Vec3Arg vel = new Vec3((float) initialVelocity.getX(), (float) initialVelocity.getY(), (float) initialVelocity.getZ());
            settings.setLinearVelocity(vel);
        }

        InertiaBodyImpl[] bodyHolder = new InertiaBodyImpl[1];
        physicsManager.queueCommand(() -> {
            Body joltBody = physicsManager.getBodyInterface().createBody(settings);
            if (joltBody == null) {
                InertiaPluginLogger.severe("Failed to create Jolt body!");
                settings.close();
                shapeSettings.close();
                return;
            }

            InertiaBodyImpl inertiaBody = new InertiaBodyImpl(joltBody, physicsManager, motionType);
            bodyHolder[0] = inertiaBody;

            physicsManager.addBody(inertiaBody);
            physicsManager.getBodyInterface().addBody(joltBody.getId(), EActivation.Activate);

            settings.close();
            shapeSettings.close();
        });

        while (bodyHolder[0] == null) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null; // Or handle it more gracefully
            }
        }

        return bodyHolder[0];
    }

    private ShapeSettings createJoltShapeSettings() {
        if (inertiaShape instanceof CubeShape cubeShape) {
            Vector halfExtents = cubeShape.getHalfExtents();
            Vec3Arg joltHalfExtents = new Vec3((float) halfExtents.getX(), (float) halfExtents.getY(), (float) halfExtents.getZ());
            return new BoxShapeSettings(joltHalfExtents);
        }
        return null;
    }

    private double calculateVolume(InertiaShape shape) {
        if (shape instanceof CubeShape cubeShape) {
            Vector he = cubeShape.getHalfExtents();
            return 8.0 * he.getX() * he.getY() * he.getZ();
        }
        return 0.0;
    }
}

