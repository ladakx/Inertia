package com.ladakx.inertia.core.impl.rendering.interaction;

import com.ladakx.inertia.api.rendering.interaction.RenderEntityInteractEvent;
import com.ladakx.inertia.api.rendering.interaction.RenderEntityInteractPayload;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionAction;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionService;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionTarget;
import com.ladakx.inertia.api.rendering.interaction.RenderInteractionTargetHit;
import com.ladakx.inertia.infrastructure.nms.network.NetworkEntityInteractionListener;
import com.ladakx.inertia.infrastructure.nms.network.NetworkManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RenderInteractionServiceImpl implements RenderInteractionService, NetworkEntityInteractionListener {

    private static final double DEFAULT_HIT_RADIUS = 0.65D;

    private record TargetState(RenderInteractionTarget target, UUID worldId, Vector position) {}

    private final Map<Integer, TargetState> byEntityId = new ConcurrentHashMap<>();

    public RenderInteractionServiceImpl(@NotNull NetworkManager networkManager) {
        Objects.requireNonNull(networkManager, "networkManager").addInteractionListener(this);
    }

    public void registerVisualTarget(int entityId,
                                     @NotNull String modelId,
                                     @NotNull String entityKey,
                                     @NotNull Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location.world is null");
        }
        byEntityId.put(entityId, new TargetState(
                new RenderInteractionTarget(entityId, modelId, entityKey),
                location.getWorld().getUID(),
                location.toVector()
        ));
    }

    public void updateVisualTargetPosition(int entityId, @NotNull Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            return;
        }
        byEntityId.computeIfPresent(entityId, (id, prev) -> new TargetState(
                prev.target(),
                location.getWorld().getUID(),
                location.toVector()
        ));
    }

    public void unregisterVisualTarget(int entityId) {
        byEntityId.remove(entityId);
    }

    @Override
    public @Nullable RenderInteractionTarget resolve(int entityId) {
        TargetState state = byEntityId.get(entityId);
        return state == null ? null : state.target();
    }

    @Override
    public @NotNull Collection<RenderInteractionTarget> getByModel(@NotNull String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        List<RenderInteractionTarget> out = new ArrayList<>();
        for (TargetState state : byEntityId.values()) {
            RenderInteractionTarget target = state.target();
            if (target.modelId().equals(modelId)) {
                out.add(target);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public @Nullable RenderInteractionTargetHit raycast(@NotNull Player player, double maxDistance) {
        Objects.requireNonNull(player, "player");
        if (maxDistance <= 0.0D) {
            return null;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() <= 1.0e-8D) {
            return null;
        }
        direction = direction.normalize();
        Vector start = eye.toVector();
        UUID worldId = player.getWorld().getUID();

        double bestDistance = Double.POSITIVE_INFINITY;
        TargetState best = null;

        for (TargetState state : byEntityId.values()) {
            if (!worldId.equals(state.worldId())) {
                continue;
            }
            Vector toTarget = state.position().clone().subtract(start);
            double projection = toTarget.dot(direction);
            if (projection < 0.0D || projection > maxDistance) {
                continue;
            }
            Vector closestPoint = direction.clone().multiply(projection);
            double perpendicularSq = toTarget.subtract(closestPoint).lengthSquared();
            if (perpendicularSq > (DEFAULT_HIT_RADIUS * DEFAULT_HIT_RADIUS)) {
                continue;
            }
            if (projection < bestDistance) {
                bestDistance = projection;
                best = state;
            }
        }

        if (best == null) {
            return null;
        }
        return new RenderInteractionTargetHit(
                best.target().entityId(),
                best.target().modelId(),
                best.target().entityKey(),
                bestDistance
        );
    }

    @Override
    public boolean onNetworkEntityInteraction(@NotNull Player player, int entityId, boolean attack) {
        TargetState state = byEntityId.get(entityId);
        if (state == null) {
            return false;
        }
        RenderInteractionTarget target = state.target();

        RenderEntityInteractPayload payload = new RenderEntityInteractPayload(
                RenderEntityInteractPayload.SCHEMA_VERSION_V1,
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getUID(),
                player.getWorld().getName(),
                entityId,
                attack ? RenderInteractionAction.ATTACK : RenderInteractionAction.INTERACT,
                target.modelId(),
                target.entityKey()
        );
        RenderEntityInteractEvent event = new RenderEntityInteractEvent(payload);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }
}
