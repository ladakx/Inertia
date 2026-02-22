package com.ladakx.inertia.api.physics;

import com.ladakx.inertia.api.body.MotionType;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Objects;

/**
 * Parameters for spawning a physics body via the public API.
 * <p>
 * Use {@link #builder(Location, PhysicsShape)} for sane defaults.
 */
public final class PhysicsBodySpec {

    private final @NotNull Location location;
    private final @NotNull Quaternionf rotation;
    private final @NotNull PhysicsShape shape;
    private final @NotNull MotionType motionType;
    private final @Nullable String bodyId;

    private final float mass;
    private final float friction;
    private final float restitution;
    private final float gravityFactor;
    private final float linearDamping;
    private final float angularDamping;
    private final @Nullable Integer objectLayer;

    private PhysicsBodySpec(Builder b) {
        this.location = b.location.clone();
        this.rotation = new Quaternionf(b.rotation);
        this.shape = b.shape;
        this.motionType = b.motionType;
        this.bodyId = b.bodyId;
        this.mass = b.mass;
        this.friction = b.friction;
        this.restitution = b.restitution;
        this.gravityFactor = b.gravityFactor;
        this.linearDamping = b.linearDamping;
        this.angularDamping = b.angularDamping;
        this.objectLayer = b.objectLayer;
    }

    public static @NotNull Builder builder(@NotNull Location location, @NotNull PhysicsShape shape) {
        return new Builder(location, shape);
    }

    public @NotNull Location location() { return location.clone(); }
    public @NotNull Quaternionf rotation() { return new Quaternionf(rotation); }
    public @NotNull PhysicsShape shape() { return shape; }
    public @NotNull MotionType motionType() { return motionType; }
    public @Nullable String bodyId() { return bodyId; }

    public float mass() { return mass; }
    public float friction() { return friction; }
    public float restitution() { return restitution; }
    public float gravityFactor() { return gravityFactor; }
    public float linearDamping() { return linearDamping; }
    public float angularDamping() { return angularDamping; }
    public @Nullable Integer objectLayer() { return objectLayer; }

    public static final class Builder {
        private final Location location;
        private final PhysicsShape shape;

        private Quaternionf rotation = new Quaternionf(); // identity
        private MotionType motionType = MotionType.DYNAMIC;
        private String bodyId = null;

        private float mass = 1.0f;
        private float friction = 0.2f;
        private float restitution = 0.0f;
        private float gravityFactor = 1.0f;
        private float linearDamping = 0.0f;
        private float angularDamping = 0.0f;
        private Integer objectLayer = null;

        private Builder(@NotNull Location location, @NotNull PhysicsShape shape) {
            this.location = Objects.requireNonNull(location, "location");
            this.shape = Objects.requireNonNull(shape, "shape");
            if (location.getWorld() == null) {
                throw new IllegalArgumentException("location.world is null");
            }
        }

        public @NotNull Builder rotation(@NotNull Quaternionf rotation) {
            this.rotation = Objects.requireNonNull(rotation, "rotation");
            return this;
        }

        public @NotNull Builder motionType(@NotNull MotionType motionType) {
            this.motionType = Objects.requireNonNull(motionType, "motionType");
            return this;
        }

        public @NotNull Builder bodyId(@Nullable String bodyId) {
            this.bodyId = bodyId;
            return this;
        }

        public @NotNull Builder mass(float mass) {
            if (mass <= 0) throw new IllegalArgumentException("mass must be > 0");
            this.mass = mass;
            return this;
        }

        public @NotNull Builder friction(float friction) {
            this.friction = friction;
            return this;
        }

        public @NotNull Builder restitution(float restitution) {
            this.restitution = restitution;
            return this;
        }

        public @NotNull Builder gravityFactor(float gravityFactor) {
            this.gravityFactor = gravityFactor;
            return this;
        }

        public @NotNull Builder linearDamping(float linearDamping) {
            this.linearDamping = linearDamping;
            return this;
        }

        public @NotNull Builder angularDamping(float angularDamping) {
            this.angularDamping = angularDamping;
            return this;
        }

        public @NotNull Builder objectLayer(@Nullable Integer objectLayer) {
            this.objectLayer = objectLayer;
            return this;
        }

        public @NotNull PhysicsBodySpec build() {
            return new PhysicsBodySpec(this);
        }
    }
}

