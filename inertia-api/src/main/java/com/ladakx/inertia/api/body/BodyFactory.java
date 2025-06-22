package com.ladakx.inertia.api.body;

/**
 * A factory for creating physics bodies.
 */
public interface BodyFactory {

    /**
     * Starts the process of building a new body.
     * @return A new BodyBuilder instance.
     */
    BodyBuilder newBuilder();
}