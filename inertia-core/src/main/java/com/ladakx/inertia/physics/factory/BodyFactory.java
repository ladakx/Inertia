package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.*;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
import com.ladakx.inertia.physics.body.impl.RagdollPhysicsBody;
import com.ladakx.inertia.physics.body.impl.TNTPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.body.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.body.config.RagdollDefinition;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
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
public class BodyFactory {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;

    public BodyFactory(InertiaPlugin plugin, PhysicsWorldRegistry physicsWorldRegistry, ConfigurationService configurationService) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
    }

    /**
     * Спавнить одиночне тіло (блок, простий об'єкт).
     */
    public boolean spawnBody(Location location, String bodyId) {
        // API already has static access (InertiaAPI.get()), but inside the core we can use managers directly or via API.
        // Since InertiaAPI implementation is also injected, strictly speaking we could use that,
        // but for internal service logic using managers is fine.
        InertiaPhysicsBody obj = InertiaAPI.get().createBody(location, bodyId);
        return obj != null;
    }

    /**
     * Спавнить ланцюг, що вільно висить або падає.
     */
    public void spawnChain(Player player, String bodyId, int size) {
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty()) {
            throw new IllegalArgumentException("Chain body not found: " + bodyId);
        }

        if (!(modelOpt.get().bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body ID '" + bodyId + "' is not of type CHAIN.");
        }
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;

        Location startLoc = getSpawnLocation(player, 3.0);
        Vector direction = new Vector(0, -1, 0);
        double spacing = def.chainSettings().spacing();

        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(0, -1, 0));
        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;

        for (int i = 0; i < size; i++) {
            Location currentLoc = startLoc.clone().add(direction.clone().multiply(i * spacing));
            RVec3 pos = new RVec3(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

            ChainPhysicsBody link = new ChainPhysicsBody(
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
        PhysicsBodyRegistry registry = configurationService.getPhysicsBodyRegistry();
        Optional<PhysicsBodyRegistry.BodyModel> modelOpt = registry.find(bodyId);
        if (modelOpt.isEmpty() || !(modelOpt.get().bodyDefinition() instanceof RagdollDefinition def)) {
            throw new IllegalArgumentException("Ragdoll body not found or invalid type: " + bodyId);
        }

        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        float yaw = -player.getLocation().getYaw() + 180;
        double yawRad = Math.toRadians(yaw);
        Quaternionf jomlQuat = new Quaternionf(new AxisAngle4f((float) yawRad, 0, 1, 0));
        Quat rotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Map<String, Body> spawnedParts = new HashMap<>();
        int totalParts = def.parts().size();
        GroupFilterTable groupFilter = new GroupFilterTable(totalParts);
        Map<String, Integer> partIndices = new HashMap<>();
        int indexCounter = 0;
        for (String key : def.parts().keySet()) {
            partIndices.put(key, indexCounter++);
        }

        String rootPart = def.parts().entrySet().stream()
                .filter(e -> e.getValue().parentName() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (rootPart == null) return;

        RVec3 rootPos = new RVec3(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());

        // Pass necessary params
        createRagdollPart(space, bodyId, rootPart, rootPos, rotation, spawnedParts, groupFilter, partIndices.get(rootPart));
        spawnRagdollChildren(space, bodyId, rootPart, def, rotation, spawnedParts, yawRad, groupFilter, partIndices);
    }

    /**
     * Спавнить безліч блоків у формі.
     */
    public int spawnShape(Player player, DebugShapeGenerator generator, String bodyId, double... params) {
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

    private void createRagdollPart(PhysicsWorld space, String bodyId, String partName, RVec3 pos, Quat rot,
                                   Map<String, Body> spawnedBodies, GroupFilterTable groupFilter, int partIndex) {
        RagdollPhysicsBody obj = new RagdollPhysicsBody(
                space, bodyId, partName, configurationService.getPhysicsBodyRegistry(),
                plugin.getRenderFactory(),
                pos, rot, spawnedBodies,
                groupFilter, partIndex
        );
        spawnedBodies.put(partName, obj.getBody());
    }

    private void spawnRagdollChildren(PhysicsWorld space, String bodyId, String parentName,
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

                    createRagdollPart(space, bodyId, partName, new RVec3(x, y, z), rotation, spawnedBodies,
                            groupFilter, partIndices.get(partName));

                    spawnRagdollChildren(space, bodyId, partName, def, rotation, spawnedBodies, yawRad, groupFilter, partIndices);
                }
            }
        });
    }

    /**
     * Spawns a physics-based TNT entity with a fuse and explosion force.
     *
     * @param location       Spawn location.
     * @param bodyId         The body ID from bodies.yml (visual/shape).
     * @param explosionForce The force applied to nearby bodies upon detonation.
     * @param velocity       Initial velocity (optional, can be null).
     */
    public void spawnTNT(Location location, String bodyId, float explosionForce, @javax.annotation.Nullable Vector velocity) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return;

        // Verify body exists
        if (getRegistry().find(bodyId).isEmpty()) {
            throw new IllegalArgumentException("Body ID not found: " + bodyId);
        }

        // Coordinates
        RVec3 pos = new RVec3(location.getX(), location.getY(), location.getZ());

        // Rotation (Identity for simplicity, or based on location yaw/pitch)
        Quat rot = new Quat(0, 0, 0, 1);

        // Create Object
        TNTPhysicsBody tnt = new TNTPhysicsBody(
                space,
                bodyId,
                getRegistry(),
                plugin.getRenderFactory(),
                pos,
                rot,
                explosionForce,
                80 // 4 seconds fuse (standard Minecraft)
        );

        // Apply initial velocity if provided (e.g., throwing)
        if (velocity != null) {
            com.github.stephengold.joltjni.Vec3 linearVel = new com.github.stephengold.joltjni.Vec3(
                    (float) velocity.getX(),
                    (float) velocity.getY(),
                    (float) velocity.getZ()
            );
            tnt.getBody().setLinearVelocity(linearVel);
        }
    }

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }

    // Helper needed for private methods to access registry
    private PhysicsBodyRegistry getRegistry() {
        return configurationService.getPhysicsBodyRegistry();
    }
}