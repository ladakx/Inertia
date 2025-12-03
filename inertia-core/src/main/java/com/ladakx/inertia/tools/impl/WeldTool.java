package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.jolt.object.AbstractPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WeldTool extends Tool {

    private @Nullable Body firstObject = null;
    private boolean keepDistance = false;

    public WeldTool() {
        super("welder");
    }

    @Override
    public void onSwapHands(Player player) {
        keepDistance = !keepDistance;
        player.sendMessage(Component.text("Weld Mode: " + (keepDistance ? "Keep Distance" : "Snap Center"), NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        if (firstObject != null) {
            firstObject = null;
            player.sendMessage("Deselected first object");
        }

        List<MinecraftSpace.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return;

        MinecraftSpace.RaycastResult result = results.get(0);
        Long va = result.va();

        // TODO: unsure
//        for (PhysicsJoint physicsJoint : rigidBody.listJoints()) {
//            rigidBody.removeJoint(physicsJoint);
//        }
        player.sendMessage(Component.text("Weld removed.", NamedTextColor.RED));
        player.playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.5f);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        List<MinecraftSpace.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return;

        MinecraftSpace.RaycastResult result = results.get(0);
        Long va = result.va();
        AbstractPhysicsObject object = space.getObjectByVa(va);
        if (object == null) return;
        Body obj = object.getBody();

        if (firstObject != null) {
            RVec3 firstObjectPos = firstObject.getPosition();
            RVec3 secondObjectPos = obj.getPosition();

            SixDofConstraintSettings settings = new SixDofConstraintSettings();
            settings.makeFixedAxis(EAxis.TranslationX);
            settings.makeFixedAxis(EAxis.TranslationY);
            settings.makeFixedAxis(EAxis.TranslationZ);
            if (keepDistance) {
                RVec3 first = new RVec3(firstObjectPos);
                first.addInPlace(-secondObjectPos.x(), -secondObjectPos.y(), -secondObjectPos.z());
                RVec3 second = new RVec3(secondObjectPos);
                first.addInPlace(-firstObjectPos.x(), -firstObjectPos.y(), -firstObjectPos.z());

                settings.setPosition1(first);
                settings.setPosition2(second);

            } else {
                settings.setPosition1(firstObjectPos);
                settings.setPosition2(secondObjectPos);
            }
            TwoBodyConstraint constraint = settings.create(firstObject, obj);
            space.addConstraint(constraint);

            space.getBodyInterface().activateBody(obj.getId());
            space.getBodyInterface().activateBody(firstObject.getId());

            firstObject = null;

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 2.0f);
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.5f, 1.5f);
        firstObject = obj;
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Welder Tool", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}