package com.ladakx.inertia.nms.v1_21_r2.network;

import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import org.bukkit.entity.Player;

public class NetworkManagerImpl implements NetworkManager {

    private final InertiaPacketInjector injector;

    public NetworkManagerImpl() {
        this.injector = new InertiaPacketInjector();
    }

    @Override
    public void inject(Player player) {
        injector.inject(player);
    }

    @Override
    public void uninject(Player player) {
        injector.uninject(player);
    }
}