package com.ladakx.inertia.api.jolt;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class JoltServices {

    public static final @NotNull ServiceKey<JoltService> JOLT =
            new ServiceKey<>("inertia.jolt", JoltService.class);

    private JoltServices() {
    }
}

