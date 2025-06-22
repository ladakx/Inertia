package com.ladakx.inertia.api.body;

import com.ladakx.inertia.api.shape.BodyShape;
import org.bukkit.Location;

/**
 * A builder interface for constructing a new physics Body.
 */
public interface BodyBuilder {

    BodyBuilder location(Location location);

    BodyBuilder type(BodyType type);

    BodyBuilder shape(BodyShape shape);

    BodyBuilder mass(double mass);

    /**
     * Finalizes the construction and adds the body to the physics world.
     * @return The newly created Body.
     */

    Body build();
}