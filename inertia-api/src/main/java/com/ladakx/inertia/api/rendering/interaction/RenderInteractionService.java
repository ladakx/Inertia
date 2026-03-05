package com.ladakx.inertia.api.rendering.interaction;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface RenderInteractionService {

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable RenderInteractionTarget resolve(int entityId);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<RenderInteractionTarget> getByModel(@NotNull String modelId);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    @Nullable RenderInteractionTargetHit raycast(@NotNull Player player, double maxDistance);
}
