package com.ladakx.inertia.api.body;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents a single rigid body in the physics world.
 */
public interface Body {

    long getID();

    Vector3f getPosition();
    Quaternionf getRotation();

    void setLocation(Location location);
    void setRotation(Quaternionf rotation);

    void applyImpulse(Vector3f impulse);

    void destroy();
}