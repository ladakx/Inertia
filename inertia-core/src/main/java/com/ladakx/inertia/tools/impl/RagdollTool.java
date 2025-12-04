package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.jolt.object.RagdollPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.config.RagdollDefinition;
import com.ladakx.inertia.physics.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RagdollTool extends Tool {

    public static final NamespacedKey BODY_ID_KEY = new NamespacedKey(InertiaPlugin.getInstance(), "ragdoll_body_id");

    public RagdollTool() {
        super("ragdoll_tool");
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), false);
    }

    @Override
    public void onSwapHands(Player player) {}

    private void spawnRagdoll(Player player, boolean applyImpulse) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) return;

        String bodyId = getBodyIdFromItem(player.getInventory().getItemInMainHand());
        if (bodyId == null) return;

        PhysicsBodyRegistry registry = ConfigManager.getInstance().getPhysicsBodyRegistry();
        var modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            player.sendMessage(Component.text("Invalid ragdoll body.", NamedTextColor.RED));
            return;
        }

        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());

        // Spawn up in the air so it falls
        Location baseLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(2.5)).add(0, 0.5, 0);

        float yaw = -player.getLocation().getYaw() + 180;
        double yawRad = Math.toRadians(yaw);

        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Map<String, Body> spawnedBodies = new HashMap<>();

        // Find Root
        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) return;

        // Spawn Root
        RVec3 rootPos = new RVec3(baseLoc.getX(), baseLoc.getY(), baseLoc.getZ());
        spawnPart(space, bodyId, rootPart, registry, rootPos, rotation, spawnedBodies);

        // Spawn Children
        spawnChildren(space, bodyId, rootPart, def, registry, rotation, spawnedBodies, yawRad);

        if (applyImpulse && spawnedBodies.containsKey(rootPart)) {
            Body rootBody = spawnedBodies.get(rootPart);
            Vector dir = player.getLocation().getDirection().multiply(1500.0);

            ThreadLocalRandom rand = ThreadLocalRandom.current();
            RVec3 impulsePos = new RVec3(
                    rootPos.xx() + rand.nextDouble(-0.3, 0.3),
                    rootPos.yy() + rand.nextDouble(-0.3, 0.3),
                    rootPos.zz() + rand.nextDouble(-0.3, 0.3)
            );
            rootBody.addImpulse(new Vec3((float)dir.getX(), (float)dir.getY(), (float)dir.getZ()), impulsePos);
        }
    }

    private void spawnChildren(MinecraftSpace space, String bodyId, String parentName,
                               RagdollDefinition def, PhysicsBodyRegistry registry,
                               Quat rotation, Map<String, Body> spawnedBodies, double yawRad) {
        Body parentBody = spawnedBodies.get(parentName);
        if (parentBody == null) return;

        RVec3 parentPos = parentBody.getPosition();
        def.parts().forEach((partName, partDef) -> {
            if (parentName.equals(partDef.parentName())) {
                RagdollDefinition.JointSettings joint = partDef.joint();
                if (joint != null) {
                    Vector pOffset = joint.pivotOnParent();
                    Vector cOffset = joint.pivotOnChild();

                    // Обертаємо вектори півотів відповідно до орієнтації регдола (yawRad)
                    Vector3d parentPivotWorld = new Vector3d(pOffset.getX(), pOffset.getY(), pOffset.getZ()).rotateY(yawRad);
                    Vector3d childPivotWorld = new Vector3d(cOffset.getX(), cOffset.getY(), cOffset.getZ()).rotateY(yawRad);

                    // ChildPos = ParentPos + (Rot * ParentOffset) - (Rot * ChildOffset)
                    // Тобто ми "зшиваємо" їх в точці півота
                    double x = parentPos.xx() + parentPivotWorld.x - childPivotWorld.x;
                    double y = parentPos.yy() + parentPivotWorld.y - childPivotWorld.y;
                    double z = parentPos.zz() + parentPivotWorld.z - childPivotWorld.z;

                    RVec3 childPos = new RVec3(x, y, z);
                    spawnPart(space, bodyId, partName, registry, childPos, rotation, spawnedBodies);
                    spawnChildren(space, bodyId, partName, def, registry, rotation, spawnedBodies, yawRad);
                }
            }
        });
    }

    private void spawnPart(MinecraftSpace space, String bodyId, String partName,
                           PhysicsBodyRegistry registry, RVec3 pos, Quat rot,
                           Map<String, Body> spawnedBodies) {
        RagdollPhysicsObject obj = new RagdollPhysicsObject(
                space, bodyId, partName, registry,
                InertiaPlugin.getInstance().getRenderFactory(),
                pos, rot, spawnedBodies
        );
        spawnedBodies.put(partName, obj.getBody());
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(BODY_ID_KEY, PersistentDataType.STRING, bodyId);
            meta.displayName(Component.text("Ragdoll: " + bodyId, NamedTextColor.GOLD));
            item.setItemMeta(meta);
        }
        return super.markItemAsTool(item);
    }

    private String getBodyIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(BODY_ID_KEY, PersistentDataType.STRING);
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.ARMOR_STAND);
    }
}