package com.ladakx.inertia.core.commands;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaBody;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.shape.CubeShape;
import com.ladakx.inertia.core.visualization.BodyVisualizer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class SpawnCubeCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BodyVisualizer bodyVisualizer;

    public SpawnCubeCommand(JavaPlugin plugin, BodyVisualizer bodyVisualizer) {
        this.plugin = plugin;
        this.bodyVisualizer = bodyVisualizer;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        Vector velocity = player.getLocation().getDirection().multiply(10);

        InertiaBody body = InertiaAPI.get().getBodyFactory().newBuilder()
                .location(player.getEyeLocation())
                .shape(new CubeShape(1, 1, 1))
                .motionType(MotionType.DYNAMIC)
                .mass(50.0)
                .initialVelocity(velocity)
                .restitution(0.5f)
                .build();

        if (body != null) {
            player.getWorld().spawn(player.getEyeLocation(), ArmorStand.class, armorStand -> {
                armorStand.setGravity(false);
                armorStand.setVisible(false);
                armorStand.setSmall(true);
                armorStand.getEquipment().setHelmet(new ItemStack(Material.OAK_WOOD));
                bodyVisualizer.visualizeBody(body, armorStand);
            });
            player.sendMessage("Spawned a physics cube!");
        } else {
            player.sendMessage("Failed to create a physics body.");
        }

        return true;
    }
}

