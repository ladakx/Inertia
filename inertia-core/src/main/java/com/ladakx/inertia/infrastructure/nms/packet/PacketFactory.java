package com.ladakx.inertia.infrastructure.nms.packet;

import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.entity.Player;
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

    /**
     * Creates a bundle packet object (if supported) wrapping the list.
     * Use sendBundle() to actually send data efficiently.
     */
    Object createBundlePacket(List<Object> packets);

    /**
     * Sends a single packet to the player.
     */
    void sendPacket(Player player, Object packet);

    /**
     * Sends a list of packets to the player.
     * Implementation should handle bundling if supported, or sequential sending otherwise.
     */
    void sendBundle(Player player, List<Object> packets);

    /**
     * Approximate packet size in bytes for per-player network budget accounting.
     */
    default int estimatePacketSizeBytes(Object packet) {
        return 256;
    }
}
