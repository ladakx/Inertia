package com.ladakx.inertia.core.body;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.api.body.BodyBuilder;
import com.ladakx.inertia.api.body.InertiaBody;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.shape.CubeShape;
import com.ladakx.inertia.api.shape.InertiaShape;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class BodyBuilderImpl implements BodyBuilder {

    private final PhysicsManager physicsManager;

    private Location location = new Location(null, 0, 0, 0);
    private InertiaShape shape = new CubeShape(1, 1, 1);
    private MotionType motionType = MotionType.STATIC;
    private double mass = 1.0;
    private Vector initialVelocity = new Vector(0, 0, 0);
    private float restitution = 0.3f;

    public BodyBuilderImpl(PhysicsManager physicsManager) {
        this.physicsManager = physicsManager;
    }

    @Override
    public BodyBuilder location(Location location) {
        this.location = location;
        return this;
    }

    @Override
    public BodyBuilder shape(InertiaShape shape) {
        this.shape = shape;
        return this;
    }

    @Override
    public BodyBuilder motionType(MotionType motionType) {
        this.motionType = motionType;
        return this;
    }

    @Override
    public BodyBuilder mass(double mass) {
        this.mass = mass;
        return this;
    }

    @Override
    public BodyBuilder initialVelocity(Vector velocity) {
        this.initialVelocity = velocity;
        return this;
    }

    @Override
    public BodyBuilder restitution(float restitution) {
        this.restitution = restitution;
        return this;
    }

    @Override
    public InertiaBody build() {
        try (ShapeSettings shapeSettings = createShapeSettings(shape)) {
            try (ShapeResult shapeResult = shapeSettings.create()) {
                if (shapeResult.hasError()) {
                    InertiaPluginLogger.severe("Failed to create shape: " + shapeResult.getError());
                    return null;
                }

                ShapeRefC joltShape = shapeResult.get();

                RVec3 position = new RVec3((float) location.getX(), (float) location.getY(), (float) location.getZ());
                Quat rotation = new Quat();
                short objectLayer = (short) (motionType == MotionType.STATIC
                        ? PhysicsManager.LAYER_NON_MOVING
                        : PhysicsManager.LAYER_MOVING);

                EMotionType joltMotionType;
                switch (motionType) {
                    case KINEMATIC -> joltMotionType = EMotionType.Kinematic;
                    case DYNAMIC -> joltMotionType = EMotionType.Dynamic;
                    default -> joltMotionType = EMotionType.Static;
                }

                try (BodyCreationSettings settings = new BodyCreationSettings(joltShape, position, rotation, joltMotionType, objectLayer)) {
                    settings.setRestitution(this.restitution);

                    if (joltMotionType == EMotionType.Dynamic) {
                        MassProperties massProperties = joltShape.getMassProperties();
                        massProperties.scaleToMass((float) mass);
                        settings.setMassPropertiesOverride(massProperties);
                        settings.setLinearVelocity(new Vec3((float) initialVelocity.getX(), (float) initialVelocity.getY(), (float) initialVelocity.getZ()));
                        massProperties.close();
                    }

                    final InertiaBodyImpl[] inertiaBodyContainer = new InertiaBodyImpl[1];

                    physicsManager.queueCommand(() -> {
                        Body joltBody = physicsManager.getBodyInterface().createBody(settings);
                        InertiaBodyImpl inertiaBody = new InertiaBodyImpl(joltBody, physicsManager, motionType);
                        inertiaBodyContainer[0] = inertiaBody;

                        physicsManager.addBody(inertiaBody);
                        physicsManager.getBodyInterface().addBody(joltBody.getId(), EActivation.Activate);
                    });

                    // Busy-wait for the body to be created on the physics thread.
                    // This is a simplification for now. In a production system, a Future/Callback would be better.
                    while (inertiaBodyContainer[0] == null) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            InertiaPluginLogger.warning("Body creation was interrupted.");
                            return null;
                        }
                    }

                    return inertiaBodyContainer[0];
                }
            }
        }
    }

    private ShapeSettings createShapeSettings(InertiaShape shape) {
        if (shape instanceof CubeShape cubeShape) {
            Vec3 halfExtents = new Vec3(
                    (float) cubeShape.getSizeX() / 2.0f,
                    (float) cubeShape.getSizeY() / 2.0f,
                    (float) cubeShape.getSizeZ() / 2.0f
            );
            return new BoxShapeSettings(halfExtents);
        }
        throw new IllegalArgumentException("Unsupported shape type: " + shape.getClass().getName());
    }
}

