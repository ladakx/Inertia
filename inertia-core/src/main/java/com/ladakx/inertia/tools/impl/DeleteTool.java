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
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) return;

        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());
        if (space == null) return;

        // Рейкаст
        List<MinecraftSpace.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 100f);

        if (!results.isEmpty()) {
            MinecraftSpace.RaycastResult result = results.get(0);
            AbstractPhysicsObject obj = space.getObjectByVa(result.va());

            if (obj != null) {
                // Візуальні ефекти перед видаленням
                Location loc = new Location(player.getWorld(), result.hitPos().xx(), result.hitPos().yy(), result.hitPos().zz());
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, SoundCategory.MASTER, 1f, 0.6f);
                player.spawnParticle(Particle.BLOCK_CRACK, loc, 10, 0.2, 0.2, 0.2, Material.STONE.createBlockData());

                // Видалення
                obj.remove();
            }
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