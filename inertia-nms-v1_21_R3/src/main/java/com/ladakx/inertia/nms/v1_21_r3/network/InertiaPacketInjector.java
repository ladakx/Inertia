package com.ladakx.inertia.nms.v1_21_r3.network;

import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class InertiaPacketInjector {

    private static final String HANDLER_NAME = "inertia_packet_injector";
    private static final int NETWORK_ENTITY_START_ID = 1_000_000_000;

    public void inject(Player player) {
        Channel channel = getChannel(player);
        if (channel == null) return;
        if (channel.pipeline().get(HANDLER_NAME) != null) return;

        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof ServerboundInteractPacket packet) {
                    int entityId = packet.getEntityId();
                    if (entityId >= NETWORK_ENTITY_START_ID) {
                        handleInteraction(player, packet, entityId);
                        return;
                    }
                }
                super.channelRead(ctx, msg);
            }
        });
    }

    public void uninject(Player player) {
        Channel channel = getChannel(player);
        if (channel == null) return;
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
    }

    private void handleInteraction(Player player, ServerboundInteractPacket packet, int entityId) {
        PhysicsWorld space = InertiaPlugin.getInstance().getSpaceManager().getSpace(player.getWorld());
        if (space == null) return;

        AbstractPhysicsBody body = space.getObjectByNetworkEntityId(entityId);

        if (body == null) return;

        boolean attack = packet.getAction() instanceof ServerboundInteractPacket.AttackAction;
        InertiaPlugin.getInstance().getToolManager().handleNetworkInteraction(player, body, attack);
    }

    private Channel getChannel(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return null;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        Connection connection = serverPlayer.connection.connection;
        return connection.channel;
    }
}
