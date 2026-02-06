package com.ladakx.inertia.physics.world.snapshot;

import com.ladakx.inertia.rendering.NetworkVisual;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VisualState {
    private NetworkVisual visual;
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f centerOffset = new Vector3f();
    private boolean rotateTranslation;
    private boolean visible;

    public void set(NetworkVisual visual, Vector3f position, Quaternionf rotation, Vector3f centerOffset, boolean rotateTranslation, boolean visible) {
        this.visual = visual;
        this.position.set(position);
        this.rotation.set(rotation);
        this.centerOffset.set(centerOffset);
        this.rotateTranslation = rotateTranslation;
        this.visible = visible;
    }

    public NetworkVisual getVisual() {
        return visual;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public Vector3f getCenterOffset() {
        return centerOffset;
    }

    public boolean isRotateTranslation() {
        return rotateTranslation;
    }

    public boolean isVisible() {
        return visible;
    }

    public void clear() {
        this.visual = null;
    }
}
