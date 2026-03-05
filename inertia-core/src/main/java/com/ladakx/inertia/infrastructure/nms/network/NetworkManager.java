package com.ladakx.inertia.infrastructure.nms.network;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface NetworkManager {
    void inject(Player player);
    void uninject(Player player);

    default void addInteractionListener(@NotNull NetworkEntityInteractionListener listener) {
    }

    default void removeInteractionListener(@NotNull NetworkEntityInteractionListener listener) {
    }
}
