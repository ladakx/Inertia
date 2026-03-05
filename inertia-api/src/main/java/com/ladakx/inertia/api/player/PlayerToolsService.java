package com.ladakx.inertia.api.player;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface PlayerToolsService {

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean absolute);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    void playSound(@NotNull Player player,
                   @NotNull String sound,
                   @NotNull SoundCategory category,
                   float volume,
                   float pitch);
}
