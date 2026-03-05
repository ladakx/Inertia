package com.ladakx.inertia.api.transport.events;

import com.ladakx.inertia.api.transport.TransportId;
import com.ladakx.inertia.api.transport.TransportInput;
import com.ladakx.inertia.api.transport.TrackedInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record TransportInputPayload(int schemaVersion,
                                    @NotNull TransportId transportId,
                                    @NotNull UUID worldId,
                                    @NotNull String worldName,
                                    @NotNull TransportInput input,
                                    @Nullable TrackedInput trackedInput) implements TransportEventPayload {
    public TransportInputPayload {
        Objects.requireNonNull(transportId, "transportId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(input, "input");
    }
}
