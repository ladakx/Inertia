package com.ladakx.inertia.tools.impl;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.jolt.object.AbstractPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class DeleteTool extends Tool {

    public DeleteTool() {
        super("remover");
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        List<MinecraftSpace.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return;

        MinecraftSpace.RaycastResult result = results.get(0);
        Long obj = result.va();

        AbstractPhysicsObject mcObj = space.getObjectByVa(obj);
        if (mcObj != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, SoundCategory.MASTER, 0.5F, 0.6F);
            mcObj.destroy();
        }
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
    }

    @Override
    public void onSwapHands(Player player) {
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.TNT_MINECART);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Remover Tool", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}