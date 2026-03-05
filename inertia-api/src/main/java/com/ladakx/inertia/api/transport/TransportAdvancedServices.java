package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class TransportAdvancedServices {

    public static final @NotNull ServiceKey<TransportAdvancedService> ADVANCED =
            new ServiceKey<>("inertia.transport.advanced", TransportAdvancedService.class);

    private TransportAdvancedServices() {
    }
}
