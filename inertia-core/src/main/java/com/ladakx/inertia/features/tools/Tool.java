package com.ladakx.inertia.features.tools;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public abstract class Tool {
    protected final String toolId;
    protected final ConfigurationService configurationService;
    protected final ToolDataManager toolDataManager;

    public Tool(String toolId, ConfigurationService configurationService, ToolDataManager toolDataManager) {
        this.toolId = toolId;
        this.configurationService = configurationService;
        this.toolDataManager = toolDataManager;
    }

    public abstract void onRightClick(PlayerInteractEvent event);
    public abstract void onLeftClick(PlayerInteractEvent event);
    public abstract void onSwapHands(Player player);

    public boolean onHotbarChange(PlayerItemHeldEvent event, int diff) {
        return false;
    }

    protected abstract ItemStack getBaseItem();

    public ItemStack buildItem() {
        return markItemAsTool(getBaseItem());
    }

    protected ItemStack markItemAsTool(ItemStack stack) {
        toolDataManager.setToolId(stack, toolId);
        return stack;
    }

    public String getId() {
        return toolId;
    }

    public boolean isTool(ItemStack stack) {
        return toolDataManager.isTool(stack, toolId);
    }

    protected boolean validateWorld(Player player) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    protected void send(CommandSender sender, MessageKey key, String... replacements) {
        configurationService.getMessageManager().send(sender, key, replacements);
    }
}