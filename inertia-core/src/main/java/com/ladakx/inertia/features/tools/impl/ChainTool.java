package com.ladakx.inertia.features.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.GroupFilterTable;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.*;

import static com.ladakx.inertia.common.pdc.InertiaPDCUtils.getString;
import static com.ladakx.inertia.common.pdc.InertiaPDCUtils.setString;
import static com.ladakx.inertia.common.utils.PlayerUtils.getTargetLocation;

public class ChainTool extends Tool {

    private static final String BODY_ID_KEY = "chain_body_id";
    private final Map<UUID, Location> startPoints = new HashMap<>();
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final JShapeFactory shapeFactory;

    public ChainTool(ConfigurationService configurationService, PhysicsWorldRegistry physicsWorldRegistry, JShapeFactory shapeFactory) {
        super("chain_tool", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.shapeFactory = shapeFactory;
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

        item = markItemAsTool(item);
        setString(InertiaPlugin.getInstance(), item, BODY_ID_KEY, bodyId);

        ItemMeta meta = item.getItemMeta();
        var msgManager = configurationService.getMessageManager();

        // 1. Display Name
        // Берем шаблон из конфига
        Component nameTemplate = msgManager.getSingle(MessageKey.TOOL_CHAIN_NAME);
        // Выполняем замену {body} -> bodyId
        meta.displayName(StringUtils.replace(nameTemplate, "{body}", bodyId));

        // 2. Lore
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

    public void buildChainBetweenPoints(Player player, Location start, Location end, String bodyId) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || modelOpt.get().bodyDefinition().type() != PhysicsBodyType.CHAIN) {
            send(player, MessageKey.INVALID_CHAIN_BODY, "{id}", bodyId);
            return;
        }

        ChainBodyDefinition def = (ChainBodyDefinition) modelOpt.get().bodyDefinition();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        Vector directionVector = end.toVector().subtract(start.toVector());
        double totalDistance = directionVector.length();
        Vector direction = directionVector.clone().normalize();

        // Используем настройки из новой секции creation
        double spacing = def.creation().spacing();

        int linkCount = (int) Math.ceil(totalDistance / spacing);
        if (linkCount < 1) linkCount = 1;

        // --- PRE-CHECK: Проверяем лимит ---
        if (!space.canSpawnBodies(linkCount + 1)) { // +1 на всякий случай или запас
            send(player, MessageKey.SPAWN_LIMIT_REACHED, "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return;
        }

        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0),
                new org.joml.Vector3f((float)direction.getX(), (float)direction.getY(), (float)direction.getZ()));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;

        GroupFilterTable groupFilter = new GroupFilterTable(linkCount + 1);

        for (int i = 0; i <= linkCount; i++) {
            if (i > 0) {
                groupFilter.disableCollision(i, i - 1);
            }

            double distanceTraveled = i * spacing;
            Vector offset = direction.clone().multiply(distanceTraveled);
            Location currentLoc = start.clone().add(offset);
            if (i == linkCount) currentLoc = end.clone();

            RVec3 pos = new RVec3(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

            try {
                ChainPhysicsBody link = new ChainPhysicsBody(
                        space,
                        bodyId,
                        registry,
                        InertiaPlugin.getInstance().getRenderFactory(),
                        shapeFactory,
                        pos,
                        linkRotation,
                        parentBody,
                        groupFilter,
                        i,
                        linkCount + 1 // [NEW] Передаем общую длину цепи
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

        send(player, MessageKey.CHAIN_CREATED, "{count}", String.valueOf(linkCount + 1));
    }
}