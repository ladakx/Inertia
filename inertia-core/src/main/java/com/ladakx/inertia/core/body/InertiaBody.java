package com.ladakx.inertia.core.body;

import com.ladakx.inertia.api.body.Body;
import com.ladakx.inertia.core.engine.PhysicsEngine;
import com.ladakx.inertia.core.ntve.JNIBridge;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Internal implementation of a physics Body.
 * This class holds the ID of the native body and delegates calls to the JNI bridge.
 */
public class InertiaBody implements Body {

    private final PhysicsEngine engine;
    private final long bodyId;

    public InertiaBody(PhysicsEngine engine, long bodyId) {
        this.engine = engine;
        this.bodyId = bodyId;
    }

    @Override
    public long getID() {
        return this.bodyId;
    }

    @Override
    public Vector3f getPosition() {
        // TODO: Implement JNIBridge.getBodyPosition(bodyId, floatArray)
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public Quaternionf getRotation() {
        // TODO: Implement JNIBridge.getBodyRotation(bodyId, floatArray)
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void setLocation(Location location) {
        // TODO: Implement JNIBridge.setBodyPosition(bodyId, x, y, z)
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void setRotation(Quaternionf rotation) {
        // TODO: Implement JNIBridge.setBodyRotation(bodyId, x, y, z, w)
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void applyImpulse(Vector3f impulse) {
        // TODO: Implement JNIBridge.applyImpulse(bodyId, x, y, z)
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void destroy() {
        JNIBridge.destroyBody(this.bodyId);
    }
}