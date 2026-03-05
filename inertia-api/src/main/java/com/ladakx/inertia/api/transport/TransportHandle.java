package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface TransportHandle extends AutoCloseable {

    @NotNull TransportId id();

    @NotNull TransportOwner owner();

    @NotNull TransportType type();

    @NotNull PhysicsBody chassis();

    @NotNull Map<String, String> customData();

    @Override
    void close();
}
