package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.physics.factory.spawner.impl.BlockSpawner;
import com.ladakx.inertia.physics.factory.spawner.impl.ChainSpawner;
import com.ladakx.inertia.physics.factory.spawner.impl.RagdollSpawner;
import com.ladakx.inertia.physics.factory.spawner.impl.TNTSpawner;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.physics.debug.shapes.DebugShapeGenerator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BodyFactory {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;
    private final JShapeFactory shapeFactory;
    private final Map<PhysicsBodyType, BodySpawner> spawners = new HashMap<>();

    public BodyFactory(InertiaPlugin plugin,
                       PhysicsWorldRegistry physicsWorldRegistry,
                       ConfigurationService configurationService,
                       JShapeFactory shapeFactory) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
        this.shapeFactory = shapeFactory;

        registerSpawners();
    }

    private void registerSpawners() {
        register(new BlockSpawner(configurationService, shapeFactory));
        register(new ChainSpawner(configurationService, shapeFactory, plugin.getRenderFactory()));
        register(new RagdollSpawner(configurationService, shapeFactory, plugin.getRenderFactory()));
        register(new TNTSpawner(configurationService, shapeFactory, plugin.getRenderFactory()));
    }

    private void register(BodySpawner spawner) {
        spawners.put(spawner.getType(), spawner);
    }

    /**
     * @deprecated Use ValidationUtils directly if needed, or rely on spawners validation.
     */
    @Deprecated
    public ValidationUtils.ValidationResult canSpawnAt(PhysicsWorld space, ShapeRefC shapeRef, RVec3 pos, Quat rot) {
        return ValidationUtils.canSpawnAt(space, shapeRef, pos, rot);
    }

    public enum ValidationResult { // Keep for backward compat for now if needed, but deprecated
        SUCCESS,
        OUT_OF_BOUNDS,
        OBSTRUCTED
    }

    public boolean spawnBody(Location location, String bodyId) {
        return spawnBody(location, bodyId, null);
    }

    public boolean spawnBody(Location location, String bodyId, @Nullable Player player) {
        if (location.getWorld() == null) return false;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return false;

        PhysicsBodyType type;
        try {
            type = configurationService.getPhysicsBodyRegistry().require(bodyId).bodyDefinition().type();
        } catch (IllegalArgumentException e) {
            InertiaLogger.warn("Cannot spawn body: " + e.getMessage());
            return false;
        }

        BodySpawner spawner = spawners.get(type);
        if (spawner == null) {
            InertiaLogger.warn("No spawner found for type: " + type);
            return false;
        }

        return spawner.spawn(new BodySpawnContext(space, location, bodyId, player, Map.of()));
    }

    public void spawnChain(Player player, String bodyId, int size) {
        if (locationIsInvalid(player.getLocation(), player)) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        BodySpawner spawner = spawners.get(PhysicsBodyType.CHAIN);
        if (spawner == null) return;

        spawner.spawn(new BodySpawnContext(space, spawnLoc, bodyId, player, Map.of("size", size)));
    }

    public void spawnRagdoll(Player player, String bodyId, boolean applyImpulse) {
        if (locationIsInvalid(player.getLocation(), player)) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        BodySpawner spawner = spawners.get(PhysicsBodyType.RAGDOLL);
        if (spawner == null) return;

        spawner.spawn(new BodySpawnContext(space, spawnLoc, bodyId, player, Map.of("impulse", applyImpulse)));
    }

    public void spawnRagdoll(Player player, String bodyId) {
        spawnRagdoll(player, bodyId, false);
    }

    public void spawnTNT(Location location, String bodyId, float explosionForce, @Nullable Vector velocity) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) return;

        BodySpawner spawner = spawners.get(PhysicsBodyType.TNT);
        if (spawner == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("force", explosionForce);
        if (velocity != null) params.put("velocity", velocity);

        spawner.spawn(new BodySpawnContext(space, location, bodyId, null, params));
    }

    public int spawnShape(Player player, DebugShapeGenerator generator, String bodyId, double... params) {
        Location center = getSpawnLocation(player, 5.0);
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return 0;

        // This is a complex multi-spawn, delegating single spawns to spawnBody
        // Not creating a shape spawner yet as it depends on generator logic.
        // We use the generic spawnBody method for each point.

        if (!space.isInsideWorld(center)) {
            configurationService.getMessageManager().send(player, MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return 0;
        }

        List<Vector> offsets = generator.generatePoints(center, params);
        if (!space.canSpawnBodies(offsets.size())) {
            configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return 0;
        }

        int count = 0;
        for (Vector offset : offsets) {
            Location loc = center.clone().add(offset);
            if (spawnBody(loc, bodyId, player)) {
                count++;
            }
        }
        return count;
    }

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }

    private boolean locationIsInvalid(Location loc, Player player) {
        // Basic checks usually done before calling spawn, but if needed:
        return loc.getWorld() == null;
    }
}