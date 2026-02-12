package com.ladakx.inertia.nms.v1_21_r2.network;

import com.ladakx.inertia.common.logging.InertiaLogger;
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

import java.lang.reflect.Field;

public class InertiaPacketInjector {

    private static final String HANDLER_NAME = "inertia_packet_injector";
    private static final int NETWORK_ENTITY_START_ID = 1_000_000_000;

    private static Field actionField;

    static {
        try {
            // Получаем доступ к приватному полю 'action'
            for (Field f : ServerboundInteractPacket.class.getDeclaredFields()) {
                if (f.getType().getName().endsWith("$Action")) { // Ищем внутренний интерфейс Action
                    f.setAccessible(true);
                    actionField = f;
                    break;
                }
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to reflect ServerboundInteractPacket action field", e);
        }
    }

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
                        return; // Отменяем обработку пакета сервером
                    }
                }
                super.channelRead(ctx, msg);
            }
        });
    }

    public void uninject(Player player) {
        Channel channel = getChannel(player);
        if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
    }

    private void handleInteraction(Player player, ServerboundInteractPacket packet, int entityId) {
        PhysicsWorld space = InertiaPlugin.getInstance().getSpaceManager().getSpace(player.getWorld());
        if (space == null) return;

        AbstractPhysicsBody body = space.getObjectByNetworkEntityId(entityId);
        if (body == null) return;

        boolean isAttack = false;
        try {
            if (actionField != null) {
                Object action = actionField.get(packet);
                if (action != null && action.getClass().getSimpleName().equals("AttackAction")) {
                    isAttack = true;
                }
            }
        } catch (Exception e) {
            InertiaLogger.error("Error reading packet action", e);
        }

        boolean finalIsAttack = isAttack;
        org.bukkit.Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            InertiaPlugin.getInstance().getToolManager().handleNetworkInteraction(player, body, finalIsAttack);
        });
    }

    private Channel getChannel(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return null;
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        Connection connection = serverPlayer.connection.connection;
        return connection.channel;
    }
}