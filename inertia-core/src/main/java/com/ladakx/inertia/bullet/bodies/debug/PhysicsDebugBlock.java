package com.ladakx.inertia.bullet.bodies.debug;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.bullet.bodies.element.PhysicsElement;
import com.ladakx.inertia.bullet.space.MinecraftSpace;
import com.ladakx.inertia.debug.DebugBlockManager;
import com.ladakx.inertia.utils.bullet.ConvertUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PhysicsDebugBlock extends PhysicsElement {

    // ************************************
    // Constants
    private static final Quaternionf EMPTY_ROT = new Quaternionf();
    private static final Vector3f SIZE = new Vector3f(1, 1, 1);

    // ************************************
    // General Fields
    private final InertiaPlugin instance;
    private final BukkitScheduler scheduler;
    private final DebugBlockManager debugBlockManager;

    // ************************************
    // Block Data
    private final BlockData activeBlockData;
    private final BlockData sleepBlockData;

    // ************************************
    // Body and space
    private final BlockDisplay display;
    private final MinecraftSpace space;
    private final PhysicsRigidBody rigidBody;

    public PhysicsDebugBlock(String block, Location loc, MinecraftSpace space, PhysicsRigidBody rigidBody) {
        super(rigidBody);
        this.instance = InertiaPlugin.getInstance();
        this.scheduler = Bukkit.getScheduler();
        this.debugBlockManager = InertiaPlugin.getBulletManager().getDebugBlockManager();

        var config = InertiaPlugin.getPConfig().GENERAL.DEBUG.debugBlocks.get(block);

        this.rigidBody = rigidBody;
        this.space = space;

        this.activeBlockData = config.activeBlockData;
        this.sleepBlockData = config.sleepBlockData;

        this.display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        this.display.setPersistent(false);

        this.display.setBlock(activeBlockData);
    }

    @Override
    public void update() {
        if (this.rigidBody.isActive()) {
            if (display == null || display.isDead()) {
                scheduler.runTask(instance, debugBlockManager::clearDebugBlocks);
                return;
            }

            Quaternionf rot = ConvertUtils.toBukkit(this.rigidBody.getPhysicsRotation(new Quaternion()));
            Vector3f offset = new Vector3f(-0.5f, -0.5f, -0.5f).rotate(rot);

            Transformation transformation = new Transformation(
                    offset,
                    rot,
                    SIZE,
                    EMPTY_ROT
            );

            scheduler.runTask(instance, () -> {
                display.setBlock(activeBlockData);
                display.setTransformation(transformation);
                display.teleport(ConvertUtils.toBukkitLoc(this.rigidBody.getPhysicsLocation(new com.jme3.math.Vector3f()), display.getWorld()));
            });
        } else {
            if (display.getBlock().getMaterial() != this.sleepBlockData.getMaterial()) {
                scheduler.runTask(instance, () -> {
                    display.setBlock(sleepBlockData);
                });
            }
        }
    }

    public BlockDisplay getDisplay() {
        return display;
    }

    public MinecraftSpace getSpace() {
        return space;
    }
}
