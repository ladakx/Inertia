package com.ladakx.inertia.physics.factory;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.factory.spawner.BodySpawnContext;
import com.ladakx.inertia.physics.factory.spawner.BodySpawner;
import com.ladakx.inertia.physics.factory.spawner.MassSpawnScheduler;
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
import java.util.UUID;

public class BodyFactory {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;
    private final JShapeFactory shapeFactory;
    private final Map<PhysicsBodyType, BodySpawner> spawners = new HashMap<>();
    private final MassSpawnScheduler massSpawnScheduler;

    public BodyFactory(InertiaPlugin plugin,
                       PhysicsWorldRegistry physicsWorldRegistry,
                       ConfigurationService configurationService,
                       JShapeFactory shapeFactory) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
        this.shapeFactory = shapeFactory;
        this.massSpawnScheduler = new MassSpawnScheduler(
                plugin,
                createMassSpawnSettings(configurationService),
                (location, job) -> spawnBody(location, job.bodyId(), job.player())
        );

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
        return spawnBodyWithResult(location, bodyId, player, Map.of()) != null;
    }

    public @Nullable InertiaPhysicsBody spawnBodyWithResult(Location location,
                                                            String bodyId,
                                                            @Nullable Player player,
                                                            Map<String, Object> params) {
        if (location.getWorld() == null) return null;
        PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
        if (space == null) return null;

        PhysicsBodyType type;
        try {
            type = configurationService.getPhysicsBodyRegistry().require(bodyId).bodyDefinition().type();
        } catch (IllegalArgumentException e) {
            InertiaLogger.warn("Cannot spawn body: " + e.getMessage());
            return null;
        }

        BodySpawner spawner = spawners.get(type);
        if (spawner == null) {
            InertiaLogger.warn("No spawner found for type: " + type);
            return null;
        }

        return spawner.spawnBody(new BodySpawnContext(space, location, bodyId, player, params));
    }

    public void spawnChain(Player player, String bodyId, int size) {
        if (locationIsInvalid(player.getLocation(), player)) return;
        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        BodySpawner spawner = spawners.get(PhysicsBodyType.CHAIN);
        if (spawner == null) return;

        spawner.spawn(new BodySpawnContext(space, spawnLoc, bodyId, player, Map.of("size", size)));
    }

    public void spawnChainAt(Player player, String bodyId, int size, Location location) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
        if (space == null) return;

        BodySpawner spawner = spawners.get(PhysicsBodyType.CHAIN);
        if (spawner == null) return;

        spawner.spawn(new BodySpawnContext(space, location, bodyId, player, Map.of("size", size)));
    }

    public void spawnChainBetween(Player player, String bodyId, Location start, Location end) {
        if (start.getWorld() == null || end.getWorld() == null) return;
        if (!start.getWorld().equals(end.getWorld())) return;

        PhysicsWorld space = physicsWorldRegistry.getWorld(start.getWorld());
        if (space == null) return;

        BodySpawner spawner = spawners.get(PhysicsBodyType.CHAIN);
        if (spawner == null) return;

        spawner.spawn(new BodySpawnContext(space, start, bodyId, player, Map.of("end", end)));
    }

    public void spawnRagdoll(Player player, String bodyId, boolean applyImpulse) {
        spawnRagdoll(player, bodyId, null, applyImpulse);
    }

    public void spawnRagdoll(Player player, String bodyId, @Nullable String skinNickname, boolean applyImpulse) {
        if (locationIsInvalid(player.getLocation(), player)) return;
        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        Location spawnLoc = getSpawnLocation(player, 3.0);

        BodySpawner spawner = spawners.get(PhysicsBodyType.RAGDOLL);
        if (spawner == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("impulse", applyImpulse);
        if (skinNickname != null && !skinNickname.isBlank()) {
            params.put("skinNickname", skinNickname);
        }
        spawner.spawn(new BodySpawnContext(space, spawnLoc, bodyId, player, params));
    }

    public void spawnRagdollAt(Player player, String bodyId, @Nullable String skinNickname, boolean applyImpulse, Location location) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
        if (space == null) return;

        BodySpawner spawner = spawners.get(PhysicsBodyType.RAGDOLL);
        if (spawner == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("impulse", applyImpulse);
        if (skinNickname != null && !skinNickname.isBlank()) {
            params.put("skinNickname", skinNickname);
        }
        spawner.spawn(new BodySpawnContext(space, location, bodyId, player, params));
    }

    public void spawnRagdoll(Player player, String bodyId) {
        spawnRagdoll(player, bodyId, false);
    }

    public void spawnTNT(Location location, String bodyId, float explosionForce, @Nullable Vector velocity) {
        if (location.getWorld() == null) return;
        PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
        if (space == null) return;

        BodySpawner spawner = spawners.get(PhysicsBodyType.TNT);
        if (spawner == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("force", explosionForce);
        if (velocity != null) params.put("velocity", velocity);

        spawner.spawn(new BodySpawnContext(space, location, bodyId, null, params));
    }

    public SpawnShapeJobResult spawnShape(Player player, DebugShapeGenerator generator, String bodyId, double... params) {
        Location center = getSpawnLocation(player, 5.0);
        return spawnShapeAt(player, generator, bodyId, center, params);
    }

    public SpawnShapeJobResult spawnShapeAt(Player player, DebugShapeGenerator generator, String bodyId, Location center, double... params) {
        PhysicsWorld space = physicsWorldRegistry.getWorld(player.getWorld());
        if (space == null) return SpawnShapeJobResult.rejected("No physics world");

        if (!space.isInsideWorld(center)) {
            configurationService.getMessageManager().send(player, MessageKey.ERROR_OCCURRED, "{error}", "Outside of world bounds!");
            return SpawnShapeJobResult.rejected("Outside of world bounds");
        }

        List<Vector> offsets = generator.generatePoints(center, params);
        if (offsets.isEmpty()) {
            return SpawnShapeJobResult.rejected("Shape has no points");
        }

        if (!space.canSpawnBodies(1)) {
            configurationService.getMessageManager().send(player, MessageKey.SPAWN_LIMIT_REACHED,
                    "{limit}", String.valueOf(space.getSettings().performance().maxBodies()));
            return SpawnShapeJobResult.rejected("World body limit reached");
        }

        MassSpawnScheduler.EnqueueResult enqueueResult = massSpawnScheduler.enqueue(player, space, center, bodyId, offsets);
        if (!enqueueResult.accepted()) {
            return SpawnShapeJobResult.rejected(enqueueResult.rejectReason() == null ? "Rejected" : enqueueResult.rejectReason());
        }

        MassSpawnScheduler.JobSnapshot snapshot = enqueueResult.snapshot();
        if (snapshot == null) {
            return SpawnShapeJobResult.rejected("Unable to create job snapshot");
        }

        return SpawnShapeJobResult.accepted(snapshot.jobId(), snapshot.total(), snapshot.progress());
    }

    public @Nullable MassSpawnScheduler.JobSnapshot getSpawnJob(UUID jobId) {
        return massSpawnScheduler.snapshot(jobId);
    }

    public void shutdown() {
        massSpawnScheduler.shutdown();
    }

    private MassSpawnScheduler.Settings createMassSpawnSettings(ConfigurationService configurationService) {
        var settings = configurationService.getInertiaConfig().PHYSICS.MASS_SPAWN;
        return new MassSpawnScheduler.Settings(
                settings.minBudgetPerTick,
                settings.baseBudgetPerTick,
                settings.maxBudgetPerTick,
                settings.warmupBudgetPerTick,
                settings.warmupTicks,
                settings.budgetIncreaseStep,
                settings.budgetDecreaseStep,
                settings.stableTpsThreshold,
                settings.stableTicksToIncreaseBudget,
                settings.maxConcurrentJobsPerWorld,
                settings.maxConcurrentJobsPerPlayer,
                settings.maxSpawnsPerJobPerTick
        );
    }

    public record SpawnShapeJobResult(boolean accepted,
                                      @Nullable UUID jobId,
                                      int totalSpawns,
                                      double progress,
                                      @Nullable String rejectReason) {
        public static SpawnShapeJobResult accepted(UUID jobId, int totalSpawns, double progress) {
            return new SpawnShapeJobResult(true, jobId, totalSpawns, progress, null);
        }

        public static SpawnShapeJobResult rejected(String reason) {
            return new SpawnShapeJobResult(false, null, 0, 0.0, reason);
        }
    }

    private Location getSpawnLocation(Player player, double distance) {
        return player.getEyeLocation().add(player.getLocation().getDirection().multiply(distance));
    }

    private boolean locationIsInvalid(Location loc, Player player) {
        // Basic checks usually done before calling spawn, but if needed:
        return loc.getWorld() == null;
    }
}
