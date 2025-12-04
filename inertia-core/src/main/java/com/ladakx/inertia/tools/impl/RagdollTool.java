package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.files.config.message.MessageKey;
import com.ladakx.inertia.jolt.object.RagdollPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RagdollTool extends Tool {

    public static final String BODY_ID_KEY = "ragdoll_body_id";

    public RagdollTool() {
        super("ragdoll_tool");
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), event.getItem(), true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        spawnRagdoll(event.getPlayer(), event.getItem(), false);
    }

    @Override
    public void onSwapHands(Player player) {}

    private void spawnRagdoll(Player player, ItemStack toolItem, boolean applyImpulse) {
        if (!validateWorld(player)) return;

        String bodyId = getString(toolItem, BODY_ID_KEY);
        if (bodyId == null) {
            send(player, MessageKey.TOOL_BROKEN_NBT);
            return;
        }

        PhysicsBodyRegistry registry = ConfigManager.getInstance().getPhysicsBodyRegistry();
        var modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            send(player, MessageKey.INVALID_RAGDOLL_BODY, "{id}", bodyId);
            return;
        }

        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());
        if (space == null) return;

        Location baseLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(2.5)).add(0, 0.5, 0);

        float yaw = -player.getLocation().getYaw() + 180;
        double yawRad = Math.toRadians(yaw);

        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Map<String, Body> spawnedBodies = new HashMap<>();

        // --- Setup Group Filter for Collisions ---
        int totalParts = def.parts().size();
        // Створюємо таблицю фільтрів з розміром = кількості частин
        GroupFilterTable groupFilter = new GroupFilterTable(totalParts);

        // Створюємо мапу PartName -> Index (0, 1, 2...)
        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        // --- Find Root ---
        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) {
            player.sendMessage(Component.text("Ragdoll definition error: No root part found!", NamedTextColor.RED));
            return;
        }

        RVec3 rootPos = new RVec3(baseLoc.getX(), baseLoc.getY(), baseLoc.getZ());

        // Спавн кореня
        spawnPart(space, bodyId, rootPart, registry, rootPos, rotation, spawnedBodies, groupFilter, partIndices.get(rootPart));

        // Спавн дітей
        spawnChildren(space, bodyId, rootPart, def, registry, rotation, spawnedBodies, yawRad, groupFilter, partIndices);

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

        send(player, MessageKey.RAGDOLL_SPAWN_SUCCESS, "{id}", bodyId);
    }

    private void spawnChildren(MinecraftSpace space, String bodyId, String parentName,
                               RagdollDefinition def, PhysicsBodyRegistry registry,
                               Quat rotation, Map<String, Body> spawnedBodies, double yawRad,
                               GroupFilterTable groupFilter, Map<String, Integer> partIndices) {
        Body parentBody = spawnedBodies.get(parentName);
        if (parentBody == null) return;

        RVec3 parentPos = parentBody.getPosition();
        def.parts().forEach((partName, partDef) -> {
            if (parentName.equals(partDef.parentName())) {
                var joint = partDef.joint();
                if (joint != null) {
                    Vector pOffset = joint.pivotOnParent();
                    Vector cOffset = joint.pivotOnChild();

                    Vector3d parentPivotWorld = new Vector3d(pOffset.getX(), pOffset.getY(), pOffset.getZ()).rotateY(yawRad);
                    Vector3d childPivotWorld = new Vector3d(cOffset.getX(), cOffset.getY(), cOffset.getZ()).rotateY(yawRad);

                    double x = parentPos.xx() + parentPivotWorld.x - childPivotWorld.x;
                    double y = parentPos.yy() + parentPivotWorld.y - childPivotWorld.y;
                    double z = parentPos.zz() + parentPivotWorld.z - childPivotWorld.z;

                    RVec3 childPos = new RVec3(x, y, z);

                    // Спавн частини з передачею фільтра та індексу
                    spawnPart(space, bodyId, partName, registry, childPos, rotation, spawnedBodies, groupFilter, partIndices.get(partName));

                    spawnChildren(space, bodyId, partName, def, registry, rotation, spawnedBodies, yawRad, groupFilter, partIndices);
                }
            }
        });
    }

    private void spawnPart(MinecraftSpace space, String bodyId, String partName,
                           PhysicsBodyRegistry registry, RVec3 pos, Quat rot,
                           Map<String, Body> spawnedBodies,
                           GroupFilterTable groupFilter, int partIndex) {
        RagdollPhysicsObject obj = new RagdollPhysicsObject(
                space, bodyId, partName, registry,
                InertiaPlugin.getInstance().getRenderFactory(),
                pos, rot, spawnedBodies,
                groupFilter, partIndex // Передаємо нові параметри
        );
        spawnedBodies.put(partName, obj.getBody());
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();
        item = markItemAsTool(item);
        setString(item, BODY_ID_KEY, bodyId);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ragdoll: " + bodyId, NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.ARMOR_STAND);
    }
}