package com.ladakx.inertia.core.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.version.ApiVersion;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.InertiaApiProvider;
import com.ladakx.inertia.core.impl.config.ConfigServiceImpl;
import com.ladakx.inertia.core.impl.capability.CapabilityServiceImpl;
import com.ladakx.inertia.core.api.body.ApiPhysicsBodyAdapter;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.impl.BlockPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import com.ladakx.inertia.core.impl.RenderingServiceImpl;

public class InertiaAPIImpl extends InertiaAPI implements InertiaApiProvider {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;
    private final RenderFactory renderFactory;
    private final JShapeFactory shapeFactory;
    private static final ApiVersion API_VERSION = new ApiVersion(1, 2, 0);

    private final RenderingService renderingService;
    private final ConfigService configService;
    private final CapabilityService capabilityService;
    private final DiagnosticsService diagnosticsService;
    private final ApiPhysicsBodyAdapter apiPhysicsBodyAdapter;

    public InertiaAPIImpl(InertiaPlugin plugin,
                          PhysicsWorldRegistry physicsWorldRegistry,
                          ConfigurationService configurationService,
                          JShapeFactory shapeFactory,
                          NetworkEntityTracker networkEntityTracker,
                          DiagnosticsService diagnosticsService) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
        this.renderFactory = plugin.getRenderFactory();
        this.shapeFactory = shapeFactory;
        this.renderingService = new RenderingServiceImpl(renderFactory, networkEntityTracker);
        this.configService = new ConfigServiceImpl();
        this.capabilityService = new CapabilityServiceImpl(API_VERSION, resolveCapabilities());
        this.apiPhysicsBodyAdapter = new ApiPhysicsBodyAdapter();
        this.diagnosticsService = Objects.requireNonNull(diagnosticsService, "diagnosticsService");
    }


    @Override
    public @NotNull InertiaAPI getApi() {
        return this;
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> createBodyResult(@NotNull Location location, @NotNull String bodyId) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(bodyId, "bodyId");
        if (location.getWorld() == null) {
            InertiaLogger.warn("Cannot create body: Location world is null.");
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }

        PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
        if (space == null) {
            return ApiResult.failure(ApiErrorCode.WORLD_NOT_SIMULATED, "not-for-this-world");
        }

        if (!space.isInsideWorld(location)) {
            InertiaLogger.debug("Attempted to spawn body '" + bodyId + "' outside world boundaries at " + location);
            return ApiResult.failure(ApiErrorCode.OUT_OF_BOUNDS, "spawn-fail-out-of-bounds");
        }

        PhysicsBodyRegistry modelRegistry = configurationService.getPhysicsBodyRegistry();
        if (modelRegistry.find(bodyId).isEmpty()) {
            InertiaLogger.warn("Cannot create body: Body ID '" + bodyId + "' not found in registry.");
            return ApiResult.failure(ApiErrorCode.BODY_NOT_FOUND, "shape-not-found");
        }

        RVec3 initialPos = space.toJolt(location);

        float yawRad = (float) Math.toRadians(-location.getYaw());
        float pitchRad = (float) Math.toRadians(location.getPitch());
        Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
        Quat initialRot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        PhysicsBodyType type = modelRegistry.require(bodyId).bodyDefinition().type();

        try {
            if (type == PhysicsBodyType.BLOCK) {
                return ApiResult.success(apiPhysicsBodyAdapter.adapt(new BlockPhysicsBody(
                        space,
                        bodyId,
                        modelRegistry,
                        renderFactory,
                        shapeFactory,
                        initialPos,
                        initialRot,
                        space.getEventDispatcher()
                )));
            } else {
                InertiaLogger.warn("Cannot create body: Unsupported body type for ID '" + bodyId + "'.");
                return ApiResult.failure(ApiErrorCode.UNSUPPORTED_BODY_TYPE, "shape-invalid-params");
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to spawn body '" + bodyId + "' via API", e);
            return ApiResult.failure(ApiErrorCode.INTERNAL_ERROR, "error-occurred");
        }
    }

    @Override
    public boolean isWorldSimulated(@NotNull String worldName) {
        return configurationService.getWorldsConfig().getWorldSettings(worldName) != null;
    }

    @Override
    public @Nullable IPhysicsWorld getPhysicsWorld(@NotNull World world) {
        return physicsWorldRegistry.getWorld(world);
    }

    @Override
    public @NotNull Collection<IPhysicsWorld> getAllPhysicsWorlds() {
        return new ArrayList<>(physicsWorldRegistry.getAllWorlds());
    }

    @Override
    public @NotNull RenderingService rendering() {
        capabilities().require(ApiCapability.RENDERING);
        return renderingService;
    }

    @Override
    public @NotNull ConfigService configs() {
        return configService;
    }

    @Override
    public @NotNull CapabilityService capabilities() {
        return capabilityService;
    }


    @Override
    public @NotNull DiagnosticsService diagnostics() {
        return diagnosticsService;
    }
    private @NotNull Set<ApiCapability> resolveCapabilities() {
        EnumSet<ApiCapability> supported = EnumSet.noneOf(ApiCapability.class);
        supported.add(ApiCapability.PHYSICS_SHAPE_BOX);
        supported.add(ApiCapability.PHYSICS_SHAPE_SPHERE);
        supported.add(ApiCapability.PHYSICS_SHAPE_CAPSULE);
        supported.add(ApiCapability.PHYSICS_SHAPE_CYLINDER);
        supported.add(ApiCapability.PHYSICS_SHAPE_TAPERED_CAPSULE);
        supported.add(ApiCapability.PHYSICS_SHAPE_TAPERED_CYLINDER);
        supported.add(ApiCapability.PHYSICS_SHAPE_CONVEX_HULL);
        supported.add(ApiCapability.PHYSICS_SHAPE_COMPOUND);
        supported.add(ApiCapability.INTERACTION_ADVANCED);
        if (renderFactory != null) {
            supported.add(ApiCapability.RENDERING);
        }
        return supported;
    }
}
