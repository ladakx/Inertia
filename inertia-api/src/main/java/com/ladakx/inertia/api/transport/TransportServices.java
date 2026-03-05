package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class TransportServices {

    public static final @NotNull ServiceKey<TransportService> TRANSPORT =
            new ServiceKey<>("inertia.transport", TransportService.class);

    private TransportServices() {
    }
}
