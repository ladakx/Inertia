package com.ladakx.inertia.api.body;

/**
 * A factory for creating physical bodies in the Inertia world.
 */
public interface BodyFactory {

    /**
     * Creates a new {@link BodyBuilder} to start the process of constructing a new physical body.
     *
     * @return A new builder instance.
     */
    BodyBuilder newBuilder();
}
