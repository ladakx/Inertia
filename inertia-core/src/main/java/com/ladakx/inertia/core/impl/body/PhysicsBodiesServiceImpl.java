package com.ladakx.inertia.core.impl.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.body.PhysicsBodiesService;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodyOwner;
import com.ladakx.inertia.api.capability.ApiCapabilities;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.events.PhysicsBodyPostSpawnEvent;
import com.ladakx.inertia.api.events.PhysicsBodyPreSpawnEvent;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.CustomPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.factory.shape.ApiShapeConverter;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class PhysicsBodiesServiceImpl implements PhysicsBodiesService {

    private final ApiShapeConverter shapeConverter = new ApiShapeConverter();
    private final @NotNull PhysicsWorldRegistry worldRegistry;

    public PhysicsBodiesServiceImpl(@NotNull PhysicsWorldRegistry worldRegistry) {
        this.worldRegistry = Objects.requireNonNull(worldRegistry, "worldRegistry");
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> spawn(@NotNull Plugin owner, @NotNull PhysicsWorld world, @NotNull PhysicsBodySpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spec, "spec");

        com.ladakx.inertia.physics.world.PhysicsWorld space = requireSpace(world);

        Location spawnLocation = spec.location();
        if (spawnLocation.getWorld() == null) {
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }
        if (!spawnLocation.getWorld().getUID().equals(space.getWorldBukkit().getUID())) {
            return ApiResult.failure(ApiErrorCode.WORLD_MISMATCH, "not-for-this-world");
        }
        if (!space.isInsideWorld(spawnLocation)) {
            return ApiResult.failure(ApiErrorCode.OUT_OF_BOUNDS, "spawn-fail-out-of-bounds");
        }

        ApiCapability shapeCapability = ApiCapabilities.forShape(spec.shape().kind());
        if (!InertiaApiAccess.resolve().capabilities().supports(shapeCapability)) {
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "shape-invalid-params");
        }

        try {
            ShapeRefC shape = shapeConverter.toJolt(spec.shape());
            RVec3 initialPos = space.toJolt(spawnLocation);
            Quaternionf rot = spec.rotation();
            Quat initialRot = new Quat(rot.x, rot.y, rot.z, rot.w);

            com.github.stephengold.joltjni.enumerate.EMotionType motionType = switch (spec.motionType()) {
                case STATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Static;
                case KINEMATIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Kinematic;
                case DYNAMIC -> com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic;
            };

            int defaultLayer = (motionType == com.github.stephengold.joltjni.enumerate.EMotionType.Static)
                    ? PhysicsLayers.OBJ_STATIC
                    : PhysicsLayers.OBJ_MOVING;
            int layer = (spec.objectLayer() != null) ? spec.objectLayer() : defaultLayer;

            BodyCreationSettings settings = new BodyCreationSettings()
                    .setShape(shape)
                    .setMotionType(motionType)
                    .setObjectLayer(layer)
                    .setLinearDamping(spec.linearDamping())
                    .setAngularDamping(spec.angularDamping());

            if (motionType == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
                settings.setMotionQuality(com.github.stephengold.joltjni.enumerate.EMotionQuality.LinearCast);
                settings.getMassProperties().setMass(spec.mass());
            }

            settings.setFriction(spec.friction());
            settings.setRestitution(spec.restitution());
            settings.setGravityFactor(spec.gravityFactor());
            settings.setPosition(initialPos);
            settings.setRotation(initialRot);

            return spawnJolt(owner, space, settings, spec.bodyId());
        } catch (Exception e) {
            InertiaLogger.error("Failed to spawn plugin body in world " + space.getWorldBukkit().getName(), e);
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "shape-invalid-params");
        }
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> spawnJolt(@NotNull Plugin owner,
                                                     @NotNull PhysicsWorld world,
                                                     @NotNull BodyCreationSettings settings,
                                                     @Nullable String bodyId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(settings, "settings");
        return spawnJolt(owner, requireSpace(world), settings, bodyId);
    }

    private @NotNull ApiResult<PhysicsBody> spawnJolt(@NotNull Plugin owner,
                                                      @NotNull com.ladakx.inertia.physics.world.PhysicsWorld space,
                                                      @NotNull BodyCreationSettings settings,
                                                      @Nullable String bodyId) {
        PhysicsBody spawnedBody = new CustomPhysicsBody(space, settings, bodyId, space.getEventDispatcher());
        if (spawnedBody instanceof AbstractPhysicsBody internal) {
            internal.setOwner(new PhysicsBodyOwner(PhysicsBodyOwner.Kind.PLUGIN, owner.getName()));
        }

        PhysicsBodyPreSpawnEvent preSpawnEvent = new PhysicsBodyPreSpawnEvent(spawnedBody);
        space.getEventDispatcher().dispatchSync(preSpawnEvent);
        if (preSpawnEvent.isCancelled()) {
            try {
                spawnedBody.destroy();
            } catch (Exception ignored) {
            }
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "spawn-cancelled");
        }

        space.getEventDispatcher().dispatchSync(new PhysicsBodyPostSpawnEvent(spawnedBody));
        return ApiResult.success(spawnedBody);
    }

    @Override
    public @NotNull PhysicsBodyOwner ownerOf(@NotNull PhysicsBody body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof AbstractPhysicsBody internal) {
            return internal.getOwner();
        }
        return PhysicsBodyOwner.unknown();
    }

    @Override
    public void destroyAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        String ownerName = owner.getName();
        for (com.ladakx.inertia.physics.world.PhysicsWorld world : worldRegistry.getAllWorlds()) {
            ArrayList<AbstractPhysicsBody> snapshot = new ArrayList<>();
            for (AbstractPhysicsBody internal : world.getObjects()) {
                PhysicsBodyOwner o = internal.getOwner();
                if (o.kind() == PhysicsBodyOwner.Kind.PLUGIN && o.id().equals(ownerName)) {
                    snapshot.add(internal);
                }
            }
            if (snapshot.isEmpty()) continue;
            for (AbstractPhysicsBody body : snapshot) {
                try {
                    body.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static @NotNull com.ladakx.inertia.physics.world.PhysicsWorld requireSpace(@NotNull PhysicsWorld world) {
        if (world instanceof com.ladakx.inertia.physics.world.PhysicsWorld space) {
            return space;
        }
        throw new IllegalArgumentException("Unsupported PhysicsWorld implementation: " + world.getClass().getName());
    }
}
