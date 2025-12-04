package com.ladakx.inertia.physics.service;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.jolt.object.ChainPhysicsObject;
import com.ladakx.inertia.jolt.object.RagdollPhysicsObject;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.ShapeGenerator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.*;

/**
 * Сервіс, що відповідає за логіку спавну фізичних об'єктів.
 * Використовує Inertia API та внутрішні механізми Jolt.
 */
public class PhysicsSpawnService {

    private final InertiaPlugin plugin;
    private final SpaceManager spaceManager;
    private final PhysicsBodyRegistry registry;

    public PhysicsSpawnService(InertiaPlugin plugin) {
        this.plugin = plugin;
        this.spaceManager = SpaceManager.getInstance();
        this.registry = ConfigManager.getInstance().getPhysicsBodyRegistry();
    }

    /**
     * Спавнить одиночне тіло (блок, простий об'єкт).
     */
    public boolean spawnBody(Location location, String bodyId) {
        InertiaPhysicsObject obj = InertiaAPI.get().createBody(location, bodyId);
        return obj != null;
    }

    /**
     * Спавнить ланцюг, що вільно висить або падає.
     */
    public void spawnChain(Player player, String bodyId, int size) {
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty()) {
            throw new IllegalArgumentException("Тіло ланцюга не знайдено: " + bodyId);
        }

        if (!(modelOpt.get().bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("ID не належить до типу ланцюга: " + bodyId);
        }

        MinecraftSpace space = spaceManager.getSpace(player.getWorld());
        if (space == null) return;

        Location startLoc = getSpawnLocation(player, 3.0);

        // Спавнимо ланцюг вертикально вниз
        Vector direction = new Vector(0, -1, 0);
        double spacing = def.chainSettings().spacing();

        // Ротація (вертикальна орієнтація)
        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, -1, 0));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;

        for (int i = 0; i < size; i++) {
            Location currentLoc = startLoc.clone().add(direction.clone().multiply(i * spacing));
            RVec3 pos = new RVec3(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

            ChainPhysicsObject link = new ChainPhysicsObject(
                    space,
                    bodyId,
                    registry,
                    plugin.getRenderFactory(),
                    pos,
                    linkRotation,
                    parentBody
            );
            parentBody = link.getBody();
        }
    }

    /**
     * Спавнить регдолл.
     */
    public void spawnRagdoll(Player player, String bodyId) {
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            throw new IllegalArgumentException("Невірний ID регдолла: " + bodyId);
        }

        MinecraftSpace space = spaceManager.getSpace(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        // Ротація відносно гравця (обличчям до гравця)
        float yaw = -player.getLocation().getYaw() + 180;
        double yawRad = Math.toRadians(yaw);
        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Map<String, Body> spawnedParts = new HashMap<>();

        // --- Setup Group Filter for Collisions ---
        int totalParts = def.parts().size();
        GroupFilterTable groupFilter = new GroupFilterTable(totalParts);

        // Map PartName -> Index
        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        // 1. Знаходимо корінь (частина без батька)
        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) return;

        // 2. Спавн кореня
        RVec3 rootPos = new RVec3(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
        createRagdollPart(space, bodyId, rootPart, rootPos, rotation, spawnedParts, groupFilter, partIndices.get(rootPart));

        // 3. Рекурсивний спавн дітей
        spawnRagdollChildren(space, bodyId, rootPart, def, rotation, spawnedParts, yawRad, groupFilter, partIndices);
    }

    /**
     * Спавнить безліч блоків у формі.
     */
    public int spawnShape(Player player, ShapeGenerator generator, String bodyId, double... params) {
        Location center = getSpawnLocation(player, 5.0); // Центр фігури перед гравцем

        List<Vector> offsets = generator.generatePoints(center, params);
        int count = 0;

        for (Vector offset : offsets) {
            Location loc = center.clone().add(offset);
            if (spawnBody(loc, bodyId)) {
                count++;
            }
        }
        return count;
    }

    // --- Private Helpers ---

    private void createRagdollPart(MinecraftSpace space, String bodyId, String partName, RVec3 pos, Quat rot,
                                   Map<String, Body> spawnedBodies, GroupFilterTable groupFilter, int partIndex) {
        RagdollPhysicsObject obj = new RagdollPhysicsObject(
                space, bodyId, partName, registry,
                plugin.getRenderFactory(),
                pos, rot, spawnedBodies,
                groupFilter, partIndex
        );
        spawnedBodies.put(partName, obj.getBody());
    }

    private void spawnRagdollChildren(MinecraftSpace space, String bodyId, String parentName,
                                      RagdollDefinition def, Quat rotation,
                                      Map<String, Body> spawnedBodies, double yawRad,
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

                    // Pass filter and index to child creation
                    createRagdollPart(space, bodyId, partName, new RVec3(x, y, z), rotation, spawnedBodies,
                            groupFilter, partIndices.get(partName));

                    spawnRagdollChildren(space, bodyId, partName, def, rotation, spawnedBodies, yawRad, groupFilter, partIndices);
                }
            }
        });
    }

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }
}