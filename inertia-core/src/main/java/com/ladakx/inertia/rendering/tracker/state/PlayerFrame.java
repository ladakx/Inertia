package com.ladakx.inertia.rendering.tracker.state;

import java.util.UUID;

public record PlayerFrame(UUID playerId, UUID worldId, double x, double y, double z, int chunkX, int chunkZ) {
}
