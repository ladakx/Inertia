package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.ladakx.inertia.jolt.object.AbstractPhysicsObject;
import com.ladakx.inertia.jolt.object.DisplayedPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.tools.Tool;
import com.ladakx.inertia.utils.jolt.ConvertUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GrabberTool extends Tool {

    private Long heldObject = null;
    private UUID holdingTask = null;
    private double holdingDistance = 0.0;
    private final double grabberForce = 10;
    private final Map<Long, GrabberJoint> jointMap = new HashMap<>();

    public GrabberTool() {
        super("grabber");
    }

    @Override
    public boolean onHotbarChange(PlayerItemHeldEvent event, int diff) {
        if (holdingTask == null) return false;
        holdingDistance += diff * 1.5;
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.5f, 2.0f);
        return true;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        if (holdingTask != null) {
            AbstractPhysicsObject mcObj = space.getObjectByVa(heldObject);
            if (mcObj instanceof DisplayedPhysicsObject displayedPhysicsObject) {
                Objects.requireNonNull(displayedPhysicsObject.getDisplay()).setGlowing(false);
            }

            space.removeTickTask(holdingTask);
            holdingTask = null;
            heldObject = null;

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, SoundCategory.MASTER, 0.5f, 1.8f);
            return;
        }

        List<MinecraftSpace.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return;

        MinecraftSpace.RaycastResult result = results.get(0);
        Long objVa = result.va();
        AbstractPhysicsObject mcObj = space.getObjectByVa(objVa);
        if (mcObj == null) return;
        Body mcBody = mcObj.getBody();

        if (jointMap.containsKey(objVa)) {
            GrabberJoint joint = jointMap.get(objVa);
            space.getBodyInterface().removeBody(joint.bodyId());
            space.removeConstraint(joint.constraint());
            jointMap.remove(objVa);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.MASTER, 0.5f, 1.8f);

        heldObject = objVa;
        holdingDistance = player.getLocation().distance(ConvertUtils.toBukkitLoc(mcBody.getPosition(), player.getWorld()));

        if (mcObj instanceof DisplayedPhysicsObject displayedPhysicsObject) {
            Objects.requireNonNull(displayedPhysicsObject.getDisplay()).setGlowing(true);
        }

        holdingTask = space.addTickTask(() -> {
            space.getBodyInterface().activateBody(mcBody.getId());
            RVec3 physicsVec = mcBody.getPosition();
            org.bukkit.util.Vector wantedPos = player.getEyeLocation().clone().add(player.getLocation().getDirection().clone().multiply(holdingDistance)).toVector();
            org.bukkit.util.Vector diff = wantedPos.subtract(ConvertUtils.toBukkit(physicsVec));
            mcBody.setLinearVelocity(ConvertUtils.toVec3(diff.multiply(grabberForce)));
        });
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        if (holdingTask == null || heldObject == null) return;
        Player player = event.getPlayer();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        AbstractPhysicsObject mcObj = space.getObjectByVa(heldObject);
        if (mcObj == null) return;
        if (mcObj instanceof DisplayedPhysicsObject displayedPhysicsObject) {
            Objects.requireNonNull(displayedPhysicsObject.getDisplay()).setGlowing(false);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.MASTER, 0.5F, 1.8F);
        player.playSound(player.getLocation(), Sound.ENTITY_BEE_STING, SoundCategory.MASTER, 0.5F, 2.0F);
        RVec3Arg physicsLoc = mcObj.getBody().getPosition();

        player.spawnParticle(Particle.REVERSE_PORTAL, ConvertUtils.toBukkitLoc(physicsLoc.toVec3(), player.getWorld()), 20);
        Body jointBody = Body.sFixedToWorld();

        SixDofConstraintSettings jointSettings = new SixDofConstraintSettings();
        jointSettings.makeFixedAxis(EAxis.TranslationX);
        jointSettings.makeFixedAxis(EAxis.TranslationY);
        jointSettings.makeFixedAxis(EAxis.TranslationZ);
        jointSettings.setPosition1(physicsLoc);
        jointSettings.setPosition2(physicsLoc);

        TwoBodyConstraint constraint = jointSettings.create(jointBody, mcObj.getBody());
        space.addConstraint(constraint);

        jointMap.put(heldObject, new GrabberJoint(constraint, jointBody.getId()));

        space.removeTickTask(holdingTask);
        holdingTask = null;
        heldObject = null;
    }

    @Override
    public void onSwapHands(Player player) {}

    @Override
    protected ItemStack getBaseItem() {
        ItemStack stack = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Grabber Tool", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));;
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private record GrabberJoint(Constraint constraint, Integer bodyId) {}
}