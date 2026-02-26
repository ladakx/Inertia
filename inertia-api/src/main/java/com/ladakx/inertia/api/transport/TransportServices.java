package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

/**
 * Well-known service keys for transport platform APIs.
 */
public final class TransportServices {

    /**
     * Main transport platform entrypoint.
     */
    public static final @NotNull ServiceKey<TransportService> TRANSPORTS =
            new ServiceKey<>("inertia.transports", TransportService.class);

    private TransportServices() {
    }
}

