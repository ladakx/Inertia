package com.ladakx.inertia.api.body;

import com.ladakx.inertia.api.shape.InertiaShape;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public interface BodyBuilder {
    BodyBuilder location(Location location);
    BodyBuilder shape(InertiaShape shape);
    BodyBuilder motionType(MotionType motionType);
    BodyBuilder mass(double mass);
    BodyBuilder initialVelocity(Vector velocity);
    BodyBuilder restitution(float restitution);
    InertiaBody build();
}

