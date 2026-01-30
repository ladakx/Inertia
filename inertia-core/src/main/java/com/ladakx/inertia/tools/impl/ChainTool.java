package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.config.message.MessageKey;
import com.ladakx.inertia.jolt.object.ChainPhysicsObject;
import com.ladakx.inertia.jolt.object.PhysicsObjectType;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ladakx.inertia.utils.PDCUtils.getString;
import static com.ladakx.inertia.utils.PDCUtils.setString;
import static com.ladakx.inertia.utils.PlayerUtils.getTargetLocation;

public class ChainTool extends Tool {

    private static final String BODY_ID_KEY = "chain_body_id";
    private final Map<UUID, Location> startPoints = new HashMap<>();
    private final SpaceManager spaceManager; // Injected

    public ChainTool(ConfigManager configManager, SpaceManager spaceManager) {
        super("chain_tool", configManager);
        this.spaceManager = spaceManager;
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        Location loc = getTargetLocation(player, event);
        startPoints.put(player.getUniqueId(), loc);

        send(player, MessageKey.CHAIN_POINT_SET, "{location}", formatLoc(loc));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
        event.setCancelled(true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        if (!startPoints.containsKey(player.getUniqueId())) {
            send(player, MessageKey.CHAIN_SELECT_FIRST);
            return;
        }

        String bodyId = getString(InertiaPlugin.getInstance(), event.getItem(), BODY_ID_KEY);
        if (bodyId == null) {
            send(player, MessageKey.CHAIN_MISSING_ID);
            return;
        }

        Location endLoc = getTargetLocation(player, event);
        Location startLoc = startPoints.remove(player.getUniqueId());

        send(player, MessageKey.CHAIN_BUILDING);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        event.setCancelled(true);

        buildChainBetweenPoints(player, startLoc, endLoc, bodyId);
    }

    @Override
    public void onSwapHands(Player player) {
        if (startPoints.remove(player.getUniqueId()) != null) {
            send(player, MessageKey.SELECTION_CLEARED);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 0.5f);
        }
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();

        // Спочатку ставимо мітку інструменту (створює метадані)
        item = markItemAsTool(item);

        // Потім додаємо ID тіла
        setString(InertiaPlugin.getInstance(), item, BODY_ID_KEY, bodyId);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Chain Tool: ", NamedTextColor.GOLD)
                .append(Component.text(bodyId, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("L-Click: Set Point 1", NamedTextColor.GRAY),
                Component.text("R-Click: Set Point 2 & Build", NamedTextColor.GRAY),
                Component.text("Swap (F): Clear selection", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);

        return item;
    }

    private String formatLoc(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.LEAD);
    }

    public void buildChainBetweenPoints(Player player, Location start, Location end, String bodyId) {
        PhysicsBodyRegistry registry = configManager.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || modelOpt.get().bodyDefinition().type() != PhysicsObjectType.CHAIN) {
            // Using logger? or send message? Tool has send()
            // InertiaLogger.warn("Invalid chain body...");
            send(player, MessageKey.INVALID_CHAIN_BODY, "{id}", bodyId);
            return;
        }

        ChainBodyDefinition def = (ChainBodyDefinition) modelOpt.get().bodyDefinition();
        MinecraftSpace space = spaceManager.getSpace(player.getWorld());
        if (space == null) return;

        Vector directionVector = end.toVector().subtract(start.toVector());
        double totalDistance = directionVector.length();
        Vector direction = directionVector.clone().normalize();
        double spacing = def.chainSettings().spacing();
        int linkCount = (int) Math.ceil(totalDistance / spacing);
        if (linkCount < 1) linkCount = 1;

        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0),
                new org.joml.Vector3f((float)direction.getX(), (float)direction.getY(), (float)direction.getZ()));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;

        for (int i = 0; i <= linkCount; i++) {
            double distanceTraveled = i * spacing;
            Vector offset = direction.clone().multiply(distanceTraveled);
            Location currentLoc = start.clone().add(offset);
            if (i == linkCount) currentLoc = end.clone();

            RVec3 pos = new RVec3(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

            try {
                ChainPhysicsObject link = new ChainPhysicsObject(
                        space,
                        bodyId,
                        registry,
                        InertiaPlugin.getInstance().getRenderFactory(),
                        pos,
                        linkRotation,
                        parentBody
                );

                if (i == 0 || i == linkCount) {
                    space.getBodyInterface().setMotionType(
                            link.getBody().getId(),
                            EMotionType.Static,
                            EActivation.DontActivate
                    );
                }
                parentBody = link.getBody();
            } catch (Exception e) {
                InertiaLogger.error("Failed to spawn chain link " + i, e);
                break;
            }
        }
        player.sendMessage(Component.text("Chain created with " + (linkCount + 1) + " links.", NamedTextColor.GRAY));
    }
}