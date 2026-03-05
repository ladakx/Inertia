package com.ladakx.inertia.core.impl.player;

import com.ladakx.inertia.api.player.PlayerToolsService;
import com.ladakx.inertia.infrastructure.nms.player.PlayerTools;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PlayerToolsServiceImpl implements PlayerToolsService {

    private final PlayerTools delegate;

    public PlayerToolsServiceImpl(@NotNull PlayerTools delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean absolute) {
        delegate.modifyCameraRotation(player, yaw, pitch, absolute);
    }

    @Override
    public void playSound(@NotNull Player player,
                          @NotNull String sound,
                          @NotNull SoundCategory category,
                          float volume,
                          float pitch) {
        delegate.playSound(player, sound, category, volume, pitch);
    }
}
