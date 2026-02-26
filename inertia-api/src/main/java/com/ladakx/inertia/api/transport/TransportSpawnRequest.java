package com.ladakx.inertia.api.transport;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Parameters for spawning a transport instance.
 */
public final class TransportSpawnRequest {
    private final TransportTypeKey type;
    private final Location location;
    private final UUID initialDriver;
    private final Map<String, Object> settings;

    public TransportSpawnRequest(@NotNull TransportTypeKey type,
                                 @NotNull Location location,
                                 @Nullable UUID initialDriver,
                                 @Nullable Map<String, Object> settings) {
        this.type = Objects.requireNonNull(type, "type");
        this.location = Objects.requireNonNull(location, "location");
        this.initialDriver = initialDriver;
        if (settings == null || settings.isEmpty()) {
            this.settings = Collections.emptyMap();
        } else {
            this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }
    }

    public @NotNull TransportTypeKey type() {
        return type;
    }

    public @NotNull Location location() {
        return location;
    }

    public @Nullable UUID initialDriver() {
        return initialDriver;
    }

    /**
     * Arbitrary, transport-type-specific settings.
     * <p>
     * Intended for simple parameterization and integration with config files.
     */
    public @NotNull Map<String, Object> settings() {
        return settings;
    }
}

