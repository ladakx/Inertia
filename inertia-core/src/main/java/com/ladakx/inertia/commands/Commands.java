package com.ladakx.inertia.commands;

import co.aikar.commands.annotation.*;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.block.BulletBlockSettings;
import com.ladakx.inertia.bullet.bodies.element.PhysicsElement;
import com.ladakx.inertia.bullet.bodies.terrarian.TerrainRigidBody;
import com.ladakx.inertia.bullet.shapes.BlockMeshShape;
import com.ladakx.inertia.bullet.shapes.util.TriangulatedBoundingBox;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.files.config.MessagesCFG;
import com.ladakx.inertia.nms.bullet.BulletNMSTools;
import com.ladakx.inertia.utils.MinecraftVersions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

@CommandAlias("rosecore|rc")
@Description("Main rosecore plugin command.")
public class Commands extends RoseCommand {

    @Subcommand("reload")
    @CommandPermission("rosevehicles.commands.reload")
    @Description("Reload the entire plugin.")
    public void onReloadCommand(CommandSender sender, @Default String arg) {
        if (checkPermission(sender, "rosecore.commands.reload", true)) {
            InertiaPlugin.getInstance().reload();
            MessagesCFG.RELOAD_PLUGIN.sendMessage(sender);
        }
    }

    @Default
    @CatchUnknown
    @Subcommand("help")
    @Description("Help plugin command")
    public void onHelp(CommandSender sender, @Default String arg) {
        if (checkPermission(sender, "rosecore.commands.help.admin", false)) {
            MessagesCFG.HELP_COMMAND_ADMIN.sendMessage(sender);
        } else if (checkPermission(sender, "rosecore.commands.help", false)) {
            MessagesCFG.HELP_COMMAND.sendMessage(sender);
        } else {
            MessagesCFG.NO_PERMISSIONS.sendMessage(sender);
        }
    }

    @Subcommand("debug bar")
    @Description("Debug command")
    public void onDebugCommand(CommandSender sender, @Default String arg) {
        if (!checkPermission(sender, "rosecore.commands.debug.bar", true)) return;
        if (!(sender instanceof Player player)) return;

        InertiaPlugin.getBulletManager().getSpaceManager().toggleDebugBar(player);
    }

    @Subcommand("debug shape create")
    @Description("Debug create shape")
    public void onCreateShape(CommandSender sender, @Default("1.0") String friction, @Default("0.025") String restitution) {
        if (!checkPermission(sender, "rosecore.commands.debug.shape", true)) return;
        if (!(sender instanceof Player player)) return;
        if (!InertiaPlugin.getInstance().isWorldEditEnabled()) {
            MessagesCFG.WORLDEDIT_NOT_ENABLED.sendMessage(player);
            return;
        }

        // check world
        World world = player.getWorld();
        if (!InertiaPlugin.getBulletManager().getSpaceManager().hasSpace(world)) {
            MessagesCFG.NOT_FOR_THIS_WORLD.sendMessage(sender);
            return;
        }

        // get bullet space
        MinecraftSpace space = InertiaPlugin.getBulletManager().getSpaceManager().getSpace(world);

        // convert string to float
        float friction0;
        float restitution0;
        try {
            friction0 = Float.parseFloat(friction);
            restitution0 = Float.parseFloat(restitution);
        } catch (Exception exp) {
            MessagesCFG.WRONG_ARGS_COMMAND.sendMessage(player);
            return;
        }

        // Adapt player to WorldEdit LocalSession
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());

        // Get the region selected by the player
        Region region;
        try {
            region = session.getRegionSelector(weWorld).getRegion();
        } catch (IncompleteRegionException e) {
            MessagesCFG.SELECT_REGION.sendMessage(player);
            return;
        }

        BlockVector3 rgMin = region.getMinimumPoint();
        BlockVector3 rgMax = region.getMaximumPoint();

        Vector3f pos1 = new Vector3f(rgMin.getX(), rgMin.getY(), rgMin.getZ());
        Vector3f pos2 = new Vector3f(rgMax.getX()+1, rgMax.getY()+1, rgMax.getZ()+1);

        Vector3f size = pos2.subtract(pos1); // (dx, dy, dz)
        TriangulatedBoundingBox localBox = new TriangulatedBoundingBox(Vector3f.ZERO, size);
        BlockMeshShape mesh = new BlockMeshShape(localBox);
        TerrainRigidBody body = new TerrainRigidBody(mesh, pos1, friction0, restitution0);
        PhysicsElement element = new PhysicsElement(body);

        space.addPhysicsElement(element);
        MessagesCFG.DEBUGSHAPE_CREATE_SUCCESS.sendMessage(player);
    }

    @Subcommand("debug block info")
    @Description("Debug block info")
    public void onBlockInfoCommand(CommandSender sender, @Default String arg) {
        if (!checkPermission(sender, "rosecore.commands.debug.block", true)) return;
        if (!(sender instanceof Player player)) return;
        BulletNMSTools nms = InertiaPlugin.getBulletNMSTools();

        Block block = player.getTargetBlock(Set.of(Material.AIR), 5);
        List<BoundingBox> list;
        if (!BulletBlockSettings.getBlockSettings(block.getType()).boxList().isEmpty()) {
            list = BulletBlockSettings.getBlockSettings(block.getType()).boxList();
        } else {
            list = nms.boundingBoxes(block.getState());
        }

        MessagesCFG.DEBUG_BLOCK_BB_TITLE.sendMessage(player, "{block}", block.getType().name());
        for (BoundingBox boundingBox : list) {
            MessagesCFG.DEBUG_BLOCK_BB_INFO.sendMessage(player,
                    "{size}", boundingBox.getExtent().toString(),
                    "{center}" , boundingBox.getCenter(new Vector3f()).toString()
            );
        }
    }

    @Subcommand("debug block clear")
    @Description("Debug blocks clear")
    public void onBlocksClearCommand(CommandSender sender, @Default String arg) {
        if (!checkPermission(sender, "rosecore.commands.debug.block", true)) return;
        if (!MinecraftVersions.isCoreVersionAboveOrEqual("1.19.4")) {
            MessagesCFG.NOT_FOR_THIS_VERSION.sendMessage(sender, "{version}", "1.19.4");
            return;
        }

        InertiaPlugin.getBulletManager().getDebugBlockManager().clearDebugBlocks();
        MessagesCFG.DEBUG_BLOCKS_CLEARED.sendMessage(sender);
    }

    @Subcommand("debug block spawn")
    @CommandCompletion("@debug-blocks 0|2|4|6|8|16")
    public void onBlockSpawnCommand(CommandSender sender, @Default("block") String block, @Default("0") String size) {
        if (!checkPermission(sender, "rosecore.commands.debug.block", true)) return;
        if (!MinecraftVersions.isCoreVersionAboveOrEqual("1.19.4")) {
            MessagesCFG.NOT_FOR_THIS_VERSION.sendMessage(sender, "{version}", "1.19.4");
            return;
        }

        if (sender instanceof Player player) {
            int radius0;
            try {radius0 = Integer.parseInt(size);
            } catch (Exception exp) {MessagesCFG.WRONG_ARGS_COMMAND.sendMessage(player);return;}

            radius0 = Math.max(0, Math.min(InertiaPlugin.getPConfig().GENERAL.DEBUG.blockDebugMaxRadius, radius0));
            Location playerLoc = new Location(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockY()+4, player.getLocation().getBlockZ());
            Location center = playerLoc.add(0.5, 0.5, 0.5);

            if (InertiaPlugin.getBulletManager().getSpaceManager().getSpace(player.getWorld()) == null) {
                MessagesCFG.NOT_FOR_THIS_WORLD.sendMessage(sender);
                return;
            }

            if (InertiaPlugin.getPConfig().GENERAL.DEBUG.debugBlocks.containsKey(block)) {
                InertiaPlugin.getBulletManager().getDebugBlockManager().generateDebugBlocks(center, radius0, block);
                MessagesCFG.DEBUG_BLOCKS_SPAWNED.sendMessage(sender, "{radius}", String.valueOf(radius0));
            } else {
                MessagesCFG.DEBUG_BLOCK_NOT_FOUND.sendMessage(sender);
            }
        } else {
            MessagesCFG.NOT_FOR_CONSOLE.sendMessage(sender);
        }
    }

    @Subcommand("hitbox")
    @Description("Enable hitbox render")
    public void onHitboxCommand(CommandSender sender, @Default String arg) {
        if (!checkPermission(sender, "rosecore.commands.hitbox", true)) return;
        if (!(sender instanceof Player player)) return;

        InertiaPlugin.enableHitboxRender = !InertiaPlugin.enableHitboxRender;
        MessagesCFG.DEBUG_RENDER_HITBOXES.sendMessage(player, "{state}", String.valueOf(InertiaPlugin.enableHitboxRender));
    }
}
