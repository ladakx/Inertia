package com.ladakx.inertia.api.player;

import com.ladakx.inertia.api.services.ServiceKey;
import org.jetbrains.annotations.NotNull;

public final class PlayerToolServices {

    public static final @NotNull ServiceKey<PlayerToolsService> TOOLS =
            new ServiceKey<>("inertia.player.tools", PlayerToolsService.class);

    private PlayerToolServices() {
    }
}
