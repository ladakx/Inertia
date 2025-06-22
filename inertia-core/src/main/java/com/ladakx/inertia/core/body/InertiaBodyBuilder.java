package com.ladakx.inertia.core.body;

import com.ladakx.inertia.api.body.Body;
import com.ladakx.inertia.api.body.BodyBuilder;
import com.ladakx.inertia.api.body.BodyType;
import com.ladakx.inertia.api.shape.BodyShape;
import com.ladakx.inertia.api.shape.BoxShape;
import com.ladakx.inertia.core.engine.PhysicsEngine;
import com.ladakx.inertia.core.ntve.JNIBridge;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class InertiaBodyBuilder implements BodyBuilder {

    private final PhysicsEngine engine;

    private Location location = new Location(null, 0, 0, 0);
    private BodyType bodyType = BodyType.STATIC;
    private BodyShape shape = null;
    private double mass = 1.0;

    public InertiaBodyBuilder(PhysicsEngine engine) {
        this.engine = engine;
    }

    @Override
    public BodyBuilder location(Location location) {
        this.location = location;
        return this;
    }

    @Override
    public BodyBuilder type(BodyType type) {
        this.bodyType = type;
        return this;
    }

    @Override
    public BodyBuilder shape(BodyShape shape) {
        this.shape = shape;
        return this;
    }

    @Override
    public BodyBuilder mass(double mass) {
        this.mass = mass;
        return this;
    }

    @Override
    public Body build() {
        if (shape == null) {
            throw new IllegalStateException("Shape must be set before building a body.");
        }

        if (!(shape instanceof BoxShape boxShape)) {
            throw new UnsupportedOperationException("Only BoxShape is supported at the moment.");
        }

        Vector3f halfExtents = boxShape.getHalfExtents();

        // Отримуємо кватерніон з локації Bukkit
        Quaternionf rotation = new Quaternionf();
        rotation.rotateY(location.getYaw() * (float) (Math.PI / 180.0));
        rotation.rotateX(location.getPitch() * (float) (Math.PI / 180.0));

        long bodyId = JNIBridge.createBoxBody(
                location.getX(), location.getY(), location.getZ(),
                rotation.x, rotation.y, rotation.z, rotation.w,
                bodyType.ordinal(),
                halfExtents.x, halfExtents.y, halfExtents.z
        );

        if (bodyId < 0) {
            throw new RuntimeException("Failed to create body in native physics world.");
        }

        return new InertiaBody(engine, bodyId);
    }
}