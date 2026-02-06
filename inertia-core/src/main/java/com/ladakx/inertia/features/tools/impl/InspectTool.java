package com.ladakx.inertia.features.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotionProperties;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;

public class InspectTool extends Tool {

    private final PhysicsWorldRegistry worldRegistry;

    public InspectTool(ConfigurationService configurationService,
                       PhysicsWorldRegistry worldRegistry,
                       ToolDataManager toolDataManager) {
        super("inspector", configurationService, toolDataManager);
        this.worldRegistry = worldRegistry;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        inspect(event.getPlayer());
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        inspect(event.getPlayer());
    }

    @Override
    public void onSwapHands(Player player) {
    }

    private void inspect(Player player) {
        if (!validateWorld(player)) return;
        PhysicsWorld space = worldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        var eye = player.getEyeLocation();
        List<PhysicsWorld.RaycastResult> hits = space.raycastEntity(
                eye,
                eye.getDirection(),
                32.0 // Long range for inspection
        );

        if (hits.isEmpty()) {
            send(player, MessageKey.DEBUG_INSPECT_MISS);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.5f);
            return;
        }

        AbstractPhysicsBody object = space.getObjectByVa(hits.get(0).va());
        if (object == null || !object.isValid()) {
            send(player, MessageKey.DEBUG_INSPECT_MISS);
            return;
        }

        Body body = object.getBody();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);

        // Gather Data
        String id = object.getBodyId();
        String uuid = object.getUuid().toString();

        EMotionType motionType = body.getMotionType();
        String typeStr = motionType.toString();
        int layer = body.getObjectLayer();

        String massStr = "Infinite";
        if (motionType == EMotionType.Dynamic) {
            MotionProperties props = body.getMotionProperties();
            if (props != null) {
                float invMass = props.getInverseMass();
                if (invMass > 0) {
                    massStr = String.format(Locale.ROOT, "%.2f", 1.0f / invMass);
                }
            }
        }

        Vector linVel = ConvertUtils.toBukkit(body.getLinearVelocity());
        Vector angVel = ConvertUtils.toBukkit(body.getAngularVelocity());
        double speed = linVel.length();

        String linVelStr = formatVector(linVel);
        String angVelStr = formatVector(angVel);
        String speedStr = String.format(Locale.ROOT, "%.2f", speed);

        float friction = body.getFriction();
        float restitution = body.getRestitution();

        String stateStr = body.isActive() ? "&aActive" : "&8Sleeping";

        // Send Messages
        MessageManager msg = configurationService.getMessageManager();
        msg.send(player, MessageKey.DEBUG_INSPECT_HEADER);
        msg.send(player, MessageKey.DEBUG_INSPECT_INFO,
                "{id}", id,
                "{uuid}", uuid,
                "{type}", typeStr,
                "{layer}", String.valueOf(layer),
                "{mass}", massStr
        );
        msg.send(player, MessageKey.DEBUG_INSPECT_VELOCITY,
                "{lin_vel}", linVelStr,
                "{speed}", speedStr,
                "{ang_vel}", angVelStr
        );
        msg.send(player, MessageKey.DEBUG_INSPECT_PROPS,
                "{friction}", String.format(Locale.ROOT, "%.2f", friction),
                "{restitution}", String.format(Locale.ROOT, "%.2f", restitution)
        );
        msg.send(player, MessageKey.DEBUG_INSPECT_STATE, "{state}", stateStr);
        msg.send(player, MessageKey.DEBUG_INSPECT_FOOTER);
    }

    private String formatVector(Vector v) {
        return String.format(Locale.ROOT, "[%.1f, %.1f, %.1f]", v.getX(), v.getY(), v.getZ());
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            MessageManager msg = configurationService.getMessageManager();
            meta.displayName(msg.getSingle(MessageKey.TOOL_INSPECT_NAME));
            meta.lore(msg.get(MessageKey.TOOL_INSPECT_LORE));
            item.setItemMeta(meta);
        }
        return item;
    }
}
