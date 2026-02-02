package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.features.tools.Tool;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RagdollTool extends Tool {
    private final BodyFactory bodyFactory;

    public RagdollTool(ConfigurationService configurationService,
                       PhysicsWorldRegistry physicsWorldRegistry,
                       JShapeFactory shapeFactory,
                       BodyFactory bodyFactory,
                       ToolDataManager toolDataManager) {
        super("ragdoll_tool", configurationService, toolDataManager);
        this.bodyFactory = bodyFactory;
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), event.getItem(), true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), event.getItem(), false);
    }

    @Override
    public void onSwapHands(Player player) {}

    private void spawnRagdoll(Player player, ItemStack toolItem, boolean applyImpulse) {
        if (!validateWorld(player)) return;

        String bodyId = toolDataManager.getBodyId(toolItem);
        if (bodyId == null) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        try {
            bodyFactory.spawnRagdoll(player, bodyId, applyImpulse);
        } catch (Exception e) {
            send(player, MessageKey.ERROR_OCCURRED, "{error}", e.getMessage());
        }
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();
        item = markItemAsTool(item);
        toolDataManager.setBodyId(item, bodyId);

        ItemMeta meta = item.getItemMeta();
        MessageManager msg = configurationService.getMessageManager();
        Component name = msg.getSingle(MessageKey.TOOL_RAGDOLL_NAME);
        meta.displayName(StringUtils.replace(name, "{body}", bodyId));
        meta.lore(msg.get(MessageKey.TOOL_RAGDOLL_LORE));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.ARMOR_STAND);
    }
}