package com.ladakx.inertia.plugin.commands;

import com.ladakx.inertia.InertiaSpigotPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaBody;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.shape.CubeShape;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the /inertiaspawn command for creating physical objects for testing.
 */
public class SpawnCubeCommand implements CommandExecutor {

    private final InertiaSpigotPlugin plugin;

    public SpawnCubeCommand(InertiaSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("cube")) {
            player.sendMessage(ChatColor.RED + "Usage: /inertiaspawn cube");
            return true;
        }

        Location spawnLocation = player.getEyeLocation().add(player.getLocation().getDirection().multiply(2));

        // 1. Create the visual entity (ArmorStand with a block)
        ArmorStand visualEntity = (ArmorStand) player.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
        visualEntity.setGravity(false); // We control gravity via physics
        visualEntity.setVisible(false); // The armor stand itself is invisible
        visualEntity.setHelmet(new ItemStack(Material.OAK_LOG));
        visualEntity.setHeadPose(new EulerAngle(0, 0, 0)); // Start with no rotation

        // 2. Create the physical body using the API
        InertiaBody physicalBody = InertiaAPI.get().getBodyFactory().newBuilder()
                .location(spawnLocation)
                .motionType(MotionType.DYNAMIC)
                .shape(new CubeShape(0.5, 0.5, 0.5)) // A standard 1x1x1 block
                .mass(50.0) // 50kg, like a dense log
                .friction(0.8f)
                .restitution(0.1f)
                .initialVelocity(player.getEyeLocation().getDirection().multiply(5)) // Give it a push
                .build();

        // 3. Link the physical body to the visual entity
        this.plugin.getBodyVisualizer().startVisualizing(physicalBody, visualEntity);

        player.sendMessage(ChatColor.GREEN + "Spawned a physical cube!");

        return true;
    }
}
