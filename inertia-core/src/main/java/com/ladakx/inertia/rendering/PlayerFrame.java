package com.ladakx.inertia.rendering;

import java.util.UUID;

record PlayerFrame(UUID playerId, UUID worldId, double x, double y, double z, int chunkX, int chunkZ) {
}
