package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.world.PhysicsWorld;
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

import static com.ladakx.inertia.common.utils.PlayerUtils.getTargetLocation;

public class ShapeTool extends Tool {
    private final BodyFactory bodyFactory;
    private final DebugShapeManager debugShapeManager;
    private final PhysicsWorldRegistry physicsWorldRegistry;

    public ShapeTool(ConfigurationService configurationService,
                     PhysicsWorldRegistry physicsWorldRegistry,
                     BodyFactory bodyFactory,
                     ToolDataManager toolDataManager) {
        super("shape_tool", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.bodyFactory = bodyFactory;
        this.debugShapeManager = new DebugShapeManager();
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        ItemStack item = event.getItem();
        String shapeName = toolDataManager.getShapeType(item);
        String bodyId = toolDataManager.getBodyId(item);
        double[] params = toolDataManager.getShapeParams(item);

        if (shapeName == null || bodyId == null) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        DebugShapeGenerator generator = debugShapeManager.getGenerator(shapeName);
        if (generator == null) {
            send(player, MessageKey.SHAPE_NOT_FOUND, "{shape}", shapeName);
            return;
        }

        try {
            int count = bodyFactory.spawnShape(player, generator, bodyId, params);
            send(player, MessageKey.SHAPE_SPAWNED, "{count}", String.valueOf(count), "{shape}", shapeName);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
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

        toolDataManager.setBodyId(item, bodyId);
        toolDataManager.setShapeData(item, shape, params);

        ItemMeta meta = item.getItemMeta();
        MessageManager msg = configurationService.getMessageManager();
        Component name = msg.getSingle(MessageKey.TOOL_SHAPE_NAME);
        meta.displayName(StringUtils.replace(name, "{shape}", shape));

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