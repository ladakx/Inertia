package com.ladakx.inertia.tools;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.utils.PDCUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import static com.ladakx.inertia.utils.PDCUtils.setString;

public abstract class Tool {

    public static final String TOOL_KEY = "inertia_tool_id";
    private final String toolId;

    public Tool(String toolId) {
        this.toolId = toolId;
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
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            send(player, MessageKey.NOT_FOR_THIS_WORLD);
            return false;
        }
        return true;
    }

    protected void send(CommandSender sender, MessageKey key, String... replacements) {
        ConfigManager.getInstance().getMessageManager().send(sender, key, replacements);
    }
}