package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of building a transport instance.
 * <p>
 * The platform will:
 * <ul>
 *     <li>track returned bodies/resources</li>
 *     <li>auto-destroy/close them when the transport is destroyed</li>
 *     <li>optionally tick a controller on the main thread</li>
 * </ul>
 */
public final class TransportBuildResult {
    private final PhysicsBody primaryBody;
    private final List<PhysicsBody> bodies;
    private final List<AutoCloseable> resources;
    private final TransportController controller;

    private TransportBuildResult(@NotNull PhysicsBody primaryBody,
                                 @NotNull List<PhysicsBody> bodies,
                                 @NotNull List<AutoCloseable> resources,
                                 @Nullable TransportController controller) {
        this.primaryBody = Objects.requireNonNull(primaryBody, "primaryBody");
        this.bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        this.resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        this.controller = controller;
        if (this.bodies.isEmpty()) {
            throw new IllegalArgumentException("bodies must not be empty");
        }
        if (!this.bodies.contains(primaryBody)) {
            throw new IllegalArgumentException("primaryBody must be included in bodies");
        }
    }

    public static @NotNull TransportBuildResult of(@NotNull PhysicsBody primaryBody,
                                                   @NotNull Collection<PhysicsBody> bodies,
                                                   @NotNull Collection<? extends AutoCloseable> resources,
                                                   @Nullable TransportController controller) {
        return new TransportBuildResult(
                primaryBody,
                List.copyOf(Objects.requireNonNull(bodies, "bodies")),
                List.copyOf(Objects.requireNonNull(resources, "resources")),
                controller
        );
    }

    public static @NotNull TransportBuildResult ofSingleBody(@NotNull PhysicsBody primaryBody,
                                                             @NotNull Collection<? extends AutoCloseable> resources,
                                                             @Nullable TransportController controller) {
        return new TransportBuildResult(
                primaryBody,
                Collections.singletonList(primaryBody),
                List.copyOf(Objects.requireNonNull(resources, "resources")),
                controller
        );
    }

    public @NotNull PhysicsBody primaryBody() {
        return primaryBody;
    }

    public @NotNull List<PhysicsBody> bodies() {
        return bodies;
    }

    public @NotNull List<AutoCloseable> resources() {
        return resources;
    }

    public @Nullable TransportController controller() {
        return controller;
    }
}

