package com.ladakx.inertia.nms.v1_21_r3.packet;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PacketFactoryImpl implements PacketFactory {

    @Override
    public Object createSpawnPacket(RenderEntityDefinition.EntityKind kind,
                                    int entityId,
                                    UUID uuid,
                                    double x,
                                    double y,
                                    double z,
                                    float yaw,
                                    float pitch) {
        EntityType<?> type = switch (kind) {
            case BLOCK_DISPLAY -> EntityType.BLOCK_DISPLAY;
            case ITEM_DISPLAY -> EntityType.ITEM_DISPLAY;
            default -> EntityType.ARMOR_STAND; // Fallback
        };

        return new ClientboundAddEntityPacket(
                entityId,
                uuid,
                x, y, z,
                pitch, yaw,
                type,
                0,
                Vec3.ZERO,
                yaw
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object createMetaPacket(int entityId, List<?> dataValues) {
        return new ClientboundSetEntityDataPacket(entityId, (List<SynchedEntityData.DataValue<?>>) dataValues);
    }

    @Override
    public Object createTeleportPacket(int entityId,
                                       double x,
                                       double y,
                                       double z,
                                       float yaw,
                                       float pitch,
                                       boolean onGround) {
        PositionMoveRotation pos = new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, pitch);
        return new ClientboundTeleportEntityPacket(entityId, pos, Collections.emptySet(), onGround);
    }

    @Override
    public Object createDestroyPacket(int... ids) {
        return new ClientboundRemoveEntitiesPacket(ids);
    }
}