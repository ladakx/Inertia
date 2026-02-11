package com.ladakx.inertia.nms.v1_16_r3.packet;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
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
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }

    @Override
    public Object createMetaPacket(int entityId, List<?> dataValues) {
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }

    @Override
    public Object createTeleportPacket(int entityId,
                                       double x,
                                       double y,
                                       double z,
                                       float yaw,
                                       float pitch,
                                       boolean onGround) {
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }

    @Override
    public Object createDestroyPacket(int... ids) {
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }

    @Override
    public Object createBundlePacket(List<Object> packets) {
        throw new UnsupportedOperationException("Bundles not supported in v1_16_R3");
    }

    @Override
    public void sendPacket(org.bukkit.entity.Player player, Object packet) {
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }

    @Override
    public void sendBundle(org.bukkit.entity.Player player, java.util.List<Object> packets) {
        throw new UnsupportedOperationException("PacketFactoryImpl is not implemented for v1_16_R3.");
    }
}