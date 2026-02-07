package com.ladakx.inertia.nms.v1_21_r3.packet;

import com.ladakx.inertia.nms.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

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
            default -> throw new IllegalArgumentException("Unsupported entity kind for packet spawn: " + kind);
        };

        return new ClientboundAddEntityPacket(
                entityId,
                uuid,
                x,
                y,
                z,
                pitch,
                yaw,
                type,
                0,
                Vec3.ZERO,
                yaw
        );
    }

    @Override
    public Object createMetaPacket(int entityId, List<?> dataValues) {
        @SuppressWarnings("unchecked")
        List<SynchedEntityData.DataValue<?>> values = (List<SynchedEntityData.DataValue<?>>) dataValues;
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    @Override
    public Object createTeleportPacket(int entityId,
                                       double x,
                                       double y,
                                       double z,
                                       float yaw,
                                       float pitch,
                                       boolean onGround) {
        return new ClientboundTeleportEntityPacket(entityId, x, y, z, yaw, pitch, onGround);
    }

    @Override
    public Object createDestroyPacket(int... ids) {
        return new ClientboundRemoveEntitiesPacket(ids);
    }
}
