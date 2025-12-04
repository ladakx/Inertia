package com.ladakx.inertia.tools.impl;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.physics.service.PhysicsSpawnService;
import com.ladakx.inertia.physics.debug.shapes.ShapeGenerator;
import com.ladakx.inertia.physics.debug.shapes.ShapeManager;
import com.ladakx.inertia.files.config.message.MessageKey;
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

import java.util.Arrays;
import java.util.List;

import static com.ladakx.inertia.utils.PDCUtils.getString;
import static com.ladakx.inertia.utils.PDCUtils.setString;

public class ShapeTool extends Tool {

    private static final String KEY_SHAPE = "shape_type";
    private static final String KEY_BODY = "shape_body";
    private static final String KEY_PARAMS = "shape_params";

    private final PhysicsSpawnService spawnService;
    private final ShapeManager shapeManager;

    public ShapeTool() {
        super("shape_tool");
        this.spawnService = new PhysicsSpawnService(InertiaPlugin.getInstance());
        this.shapeManager = new ShapeManager();
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

        ShapeGenerator generator = shapeManager.getGenerator(shapeName);
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
                if (spawnService.spawnBody(loc, bodyId)) {
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
        meta.displayName(Component.text("Shape Tool: ", NamedTextColor.GOLD)
                .append(Component.text(shape, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("Body: ", NamedTextColor.GRAY).append(Component.text(bodyId, NamedTextColor.WHITE)),
                Component.text("Params: ", NamedTextColor.GRAY).append(Component.text(Arrays.toString(params), NamedTextColor.WHITE)),
                Component.empty(),
                Component.text("R-Click: Spawn Shape", NamedTextColor.GREEN)
        ));
        item.setItemMeta(meta);

        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.SLIME_BALL);
    }
}