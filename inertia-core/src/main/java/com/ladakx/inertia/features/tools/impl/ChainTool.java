package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

import static com.ladakx.inertia.common.utils.PlayerUtils.getTargetLocation;

public class ChainTool extends Tool {
    private final Map<UUID, SelectionPoint> startPoints = new HashMap<>();
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final JShapeFactory shapeFactory;
    private final BodyFactory bodyFactory;

    private record SelectionPoint(Location location, Vector normal) {}

    public ChainTool(ConfigurationService configurationService,
                     PhysicsWorldRegistry physicsWorldRegistry,
                     JShapeFactory shapeFactory,
                     BodyFactory bodyFactory,
                     ToolDataManager toolDataManager) {
        super("chain_tool", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.shapeFactory = shapeFactory;
        this.bodyFactory = bodyFactory;
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        Location loc = getTargetLocation(player, event);
        startPoints.put(player.getUniqueId(), new SelectionPoint(loc, getClickNormal(event)));
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

        String bodyId = toolDataManager.getBodyId(event.getItem());
        if (bodyId == null) {
            send(player, MessageKey.CHAIN_MISSING_ID);
            return;
        }

        Location endLoc = getTargetLocation(player, event);
        SelectionPoint startSelection = startPoints.remove(player.getUniqueId());
        SelectionPoint endSelection = new SelectionPoint(endLoc, getClickNormal(event));

        send(player, MessageKey.CHAIN_BUILDING);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        event.setCancelled(true);

        buildChainBetweenPoints(player, startSelection, endSelection, bodyId);
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
        item = markItemAsTool(item);
        toolDataManager.setBodyId(item, bodyId);

        ItemMeta meta = item.getItemMeta();
        var msgManager = configurationService.getMessageManager();
        Component nameTemplate = msgManager.getSingle(MessageKey.TOOL_CHAIN_NAME);
        meta.displayName(StringUtils.replace(nameTemplate, "{body}", bodyId));
        List<Component> lore = msgManager.get(MessageKey.TOOL_CHAIN_LORE);
        meta.lore(lore);
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

    public void buildChainBetweenPoints(Player player, SelectionPoint start, SelectionPoint end, String bodyId) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty() || modelOpt.get().bodyDefinition().type() != PhysicsBodyType.CHAIN) {
            send(player, MessageKey.INVALID_CHAIN_BODY, "{id}", bodyId);
            return;
        }

        ChainBodyDefinition def = (ChainBodyDefinition) modelOpt.get().bodyDefinition();
        double surfaceOffset = Math.max(def.creation().jointOffset(), def.creation().spacing() * 0.5) + 0.01;

        Location startLoc = applySurfaceOffset(start.location(), start.normal(), surfaceOffset);
        Location endLoc = applySurfaceOffset(end.location(), end.normal(), surfaceOffset);

        bodyFactory.spawnChainBetween(player, bodyId, startLoc, endLoc);
    }

    private static Vector getClickNormal(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return null;
        BlockFace face = event.getBlockFace();
        if (face == null) return null;
        Vector dir = face.getDirection();
        return dir.lengthSquared() > 0 ? dir.clone().normalize() : null;
    }

    private static Location applySurfaceOffset(Location loc, Vector normal, double offset) {
        if (normal == null) return loc;
        return loc.clone().add(normal.clone().multiply(offset));
    }
}
