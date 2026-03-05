package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class TransportNativeServices {

    public static final @NotNull ServiceKey<TransportNativeService> NATIVE =
            new ServiceKey<>("inertia.transport.native", TransportNativeService.class);

    private TransportNativeServices() {
    }
}
