package com.ladakx.inertia.api.transport;

import org.jetbrains.annotations.NotNull;

/**
 * Handle for a registered {@link TransportType}.
 */
public interface TransportTypeHandle extends AutoCloseable {
    @NotNull TransportTypeKey key();

    @Override
    void close();
}

