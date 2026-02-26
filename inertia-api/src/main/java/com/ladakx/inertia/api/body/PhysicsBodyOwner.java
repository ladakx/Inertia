package com.ladakx.inertia.api.body;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Ownership metadata for a {@link PhysicsBody}.
 * <p>
 * Used by Inertia to distinguish bodies created by Inertia itself vs. bodies created by third-party plugins.
 */
public record PhysicsBodyOwner(@NotNull Kind kind, @NotNull String id) {

    public enum Kind {
        /**
         * Body was created by Inertia's own features / test objects.
         */
        INERTIA,
        /**
         * Body was created by a third-party plugin via public APIs.
         */
        PLUGIN,
        /**
         * Ownership is unknown (legacy / foreign body implementation).
         */
        UNKNOWN
    }

    public PhysicsBodyOwner {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
    }

    public static @NotNull PhysicsBodyOwner inertia() {
        return new PhysicsBodyOwner(Kind.INERTIA, "Inertia");
    }

    public static @NotNull PhysicsBodyOwner unknown() {
        return new PhysicsBodyOwner(Kind.UNKNOWN, "unknown");
    }
}

