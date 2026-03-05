package com.ladakx.inertia.nms.v1_21_r2.network;

import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import com.ladakx.inertia.infrastructure.nms.network.NetworkEntityInteractionListener;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NetworkManagerImpl implements NetworkManager {

    private final InertiaPacketInjector injector;
    private final List<NetworkEntityInteractionListener> interactionListeners = new CopyOnWriteArrayList<>();

    public NetworkManagerImpl() {
        this.injector = new InertiaPacketInjector(interactionListeners);
    }

    @Override
    public void inject(Player player) {
        injector.inject(player);
    }

    @Override
    public void uninject(Player player) {
        injector.uninject(player);
    }

    @Override
    public void addInteractionListener(@NotNull NetworkEntityInteractionListener listener) {
        interactionListeners.add(listener);
    }

    @Override
    public void removeInteractionListener(@NotNull NetworkEntityInteractionListener listener) {
        interactionListeners.remove(listener);
    }
}
