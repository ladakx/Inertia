package com.ladakx.inertia.features.tools;

import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.common.utils.PDCUtils;
import com.ladakx.inertia.physics.factory.BodyFactory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import static com.ladakx.inertia.common.utils.PDCUtils.setString;

public abstract class Tool {

    public static final String TOOL_KEY = "inertia_tool_id";
    protected final String toolId;
    protected final ConfigurationService configurationService;

    public Tool(String toolId, ConfigurationService configurationService) {
        this.toolId = toolId;
        this.configurationService = configurationService;
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
        // Plugin instance needed for NamespacedKey. Can be passed or retrieved from configManager if we stored it there,
        // or kept static usage for InertiaPlugin.getInstance() solely for NamespacedKeys (acceptable in Spigot API context).
        setString(InertiaPlugin.getInstance(), stack, TOOL_KEY, toolId);
        return stack;
    }

    public String getId() {
        return toolId;
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null) return false;
        String id = PDCUtils.getString(InertiaPlugin.getInstance(), stack, TOOL_KEY);
        return toolId.equals(id);
    }

    // --- Helper Methods ---

    protected boolean validateWorld(Player player) {
        // InertiaAPI.get() is static, allowed for public API usage.
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    protected void send(CommandSender sender, MessageKey key, String... replacements) {
        // DI usage
        configurationService.getMessageManager().send(sender, key, replacements);
    }
}