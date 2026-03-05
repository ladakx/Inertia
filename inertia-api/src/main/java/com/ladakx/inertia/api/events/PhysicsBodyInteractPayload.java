package com.ladakx.inertia.api.events;

import com.ladakx.inertia.api.body.PhysicsBody;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PhysicsBodyInteractPayload(int schemaVersion,
                                         @NotNull UUID playerUuid,
                                         @NotNull String playerName,
                                         @NotNull UUID worldUuid,
                                         @NotNull String worldName,
                                         @NotNull PhysicsBodyInteractAction action,
                                         @NotNull PhysicsBody body,
                                         @NotNull Vector point,
                                         double fraction) {
}
