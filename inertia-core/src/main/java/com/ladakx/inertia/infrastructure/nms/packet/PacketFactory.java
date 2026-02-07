package com.ladakx.inertia.infrastructure.nms.packet;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;

import java.util.List;
import java.util.UUID;

public interface PacketFactory {

    Object createSpawnPacket(RenderEntityDefinition.EntityKind kind,
                             int entityId,
                             UUID uuid,
                             double x,
                             double y,
                             double z,
                             float yaw,
                             float pitch);

    Object createMetaPacket(int entityId, List<?> dataValues);

    Object createTeleportPacket(int entityId,
                                double x,
                                double y,
                                double z,
                                float yaw,
                                float pitch,
                                boolean onGround);

    Object createDestroyPacket(int... ids);
}
