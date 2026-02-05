package com.ladakx.inertia.physics.world.terrain.profile;

import com.github.stephengold.joltjni.AaBox;

import java.util.List;
import java.util.Objects;

public final class PhysicalProfile {

    private final String id;
    private final float density;
    private final float friction;
    private final float restitution;
    private final List<AaBox> boundingBoxes;

    public PhysicalProfile(String id, float density, float friction, float restitution, List<AaBox> boundingBoxes) {
        this.id = Objects.requireNonNull(id, "id");
        this.density = density;
        this.friction = friction;
        this.restitution = restitution;
        this.boundingBoxes = List.copyOf(Objects.requireNonNull(boundingBoxes, "boundingBoxes"));
    }

    public String id() {
        return id;
    }

    public float density() {
        return density;
    }

    public float friction() {
        return friction;
    }

    public float restitution() {
        return restitution;
    }

    public List<AaBox> boundingBoxes() {
        return boundingBoxes;
    }

    public PhysicalProfile withBoundingBoxes(List<AaBox> boxes) {
        return new PhysicalProfile(id, density, friction, restitution, boxes);
    }

    public PhysicalProfile withOverrides(Float densityOverride, Float frictionOverride, Float restitutionOverride) {
        float nextDensity = densityOverride != null ? densityOverride : density;
        float nextFriction = frictionOverride != null ? frictionOverride : friction;
        float nextRestitution = restitutionOverride != null ? restitutionOverride : restitution;
        return new PhysicalProfile(id, nextDensity, nextFriction, nextRestitution, boundingBoxes);
    }
}
