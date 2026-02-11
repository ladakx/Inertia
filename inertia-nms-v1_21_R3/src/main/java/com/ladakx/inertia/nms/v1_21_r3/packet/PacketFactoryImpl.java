package com.ladakx.inertia.nms.v1_21_r3.packet;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
            default -> EntityType.ARMOR_STAND;
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

    @Override
    @SuppressWarnings("unchecked")
    public Object createBundlePacket(List<Object> packets) {
        // Instead of returning ClientboundBundlePacket which might be causing EncoderException
        // due to missing mappings or conflicts, we return the List itself.
        // The sendPacket/sendBundle methods will handle flattening this list.
        return new ArrayList<>(packets);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        if (packet instanceof List<?> list) {
            for (Object p : list) {
                sendPacket(player, p);
            }
            return;
        }

        if (packet instanceof Packet<?> nmsPacket && player instanceof CraftPlayer craftPlayer) {
            ServerPlayer serverPlayer = craftPlayer.getHandle();
            serverPlayer.connection.send(nmsPacket);
        }
    }

    @Override
    public void sendBundle(Player player, List<Object> packets) {
        if (packets == null || packets.isEmpty()) return;

        for (Object p : packets) {
            sendPacket(player, p);
        }
    }
}