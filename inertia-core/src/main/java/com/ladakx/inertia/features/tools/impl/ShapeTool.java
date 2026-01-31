package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeManager;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ladakx.inertia.common.utils.PDCUtils.getString;
import static com.ladakx.inertia.common.utils.PDCUtils.setString;
import static com.ladakx.inertia.common.utils.PlayerUtils.getTargetLocation;

public class ShapeTool extends Tool {

    private static final String KEY_SHAPE = "shape_type";
    private static final String KEY_BODY = "shape_body";
    private static final String KEY_PARAMS = "shape_params";

    private final BodyFactory bodyFactory;
    private final DebugShapeManager debugShapeManager;
    private final PhysicsWorldRegistry physicsWorldRegistry;

    public ShapeTool(ConfigurationService configurationService,
                     PhysicsWorldRegistry physicsWorldRegistry,
                     BodyFactory bodyFactory) {
        super("shape_tool", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.bodyFactory = bodyFactory;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        // Placeholder
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        ItemStack item = event.getItem();
        String shapeName = getString(InertiaPlugin.getInstance(), item, KEY_SHAPE);
        String bodyId = getString(InertiaPlugin.getInstance(), item, KEY_BODY);
        String paramsStr = getString(InertiaPlugin.getInstance(), item, KEY_PARAMS);

        if (shapeName == null || bodyId == null) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        DebugShapeGenerator generator = debugShapeManager.getGenerator(shapeName);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeName);
            return;
        }

        double[] params;
        try {
            params = Arrays.stream(paramsStr.split(";")).mapToDouble(Double::parseDouble).toArray();
        } catch (Exception e) {
            send(player, MessageKey.TOOL_INVALID_PARAMS);
            return;
        }

        try {
            Location center = getTargetLocation(player, event);
            center.add(0, 1, 0);

            List<org.bukkit.util.Vector> offsets = generator.generatePoints(center, params);
            int count = 0;
            for (org.bukkit.util.Vector offset : offsets) {
                Location loc = center.clone().add(offset);
                if (bodyFactory.spawnBody(loc, bodyId)) {
                    count++;
                }
            }

            send(player, MessageKey.SHAPE_SPAWNED, "{count}", String.valueOf(count), "{shape}", shapeName);
            player.playSound(center, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

        } catch (Exception e) {
            send(player, MessageKey.SHAPE_SPAWN_ERROR, "{error}", e.getMessage());
        }
    }

    @Override
    public void onSwapHands(Player player) {
    }

    public ItemStack getToolItem(String shape, String bodyId, double[] params) {
        ItemStack item = getBaseItem();
        item = markItemAsTool(item);

        String storedParams = String.join(";", Arrays.stream(params).mapToObj(String::valueOf).toArray(String[]::new));
        setString(InertiaPlugin.getInstance(), item, KEY_SHAPE, shape);
        setString(InertiaPlugin.getInstance(), item, KEY_BODY, bodyId);
        setString(InertiaPlugin.getInstance(), item, KEY_PARAMS, storedParams);

        ItemMeta meta = item.getItemMeta();
        MessageManager msg = configurationService.getMessageManager();

        // Localized Name
        Component name = msg.getSingle(MessageKey.TOOL_SHAPE_NAME);
        meta.displayName(StringUtils.replace(name, "{shape}", shape));

        // Localized Lore (List with replacements)
        List<Component> lore = new ArrayList<>();
        for (Component line : msg.get(MessageKey.TOOL_SHAPE_LORE)) {
            lore.add(StringUtils.replace(line,
                    "{body}", bodyId,
                    "{params}", Arrays.toString(params)
            ));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.SLIME_BALL);
    }
}