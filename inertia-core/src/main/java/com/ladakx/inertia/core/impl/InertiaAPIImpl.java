package com.ladakx.inertia.core.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApi;
import com.ladakx.inertia.api.InertiaApiProvider;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodiesService;
import com.ladakx.inertia.api.body.PhysicsBodyServices;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.config.ConfigService;
import com.ladakx.inertia.api.diagnostics.DiagnosticsService;
import com.ladakx.inertia.api.extension.ExtensionContext;
import com.ladakx.inertia.api.extension.ExtensionHandle;
import com.ladakx.inertia.api.extension.ExtensionRegistry;
import com.ladakx.inertia.api.extension.InertiaExtension;
import com.ladakx.inertia.api.rendering.RenderingService;
import com.ladakx.inertia.api.rendering.model.RenderModelRegistryService;
import com.ladakx.inertia.api.rendering.model.RenderingModelServices;
import com.ladakx.inertia.api.services.ServiceKey;
import com.ladakx.inertia.api.services.ServiceRegistry;
import com.ladakx.inertia.api.version.ApiVersion;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.core.impl.capability.CapabilityServiceImpl;
import com.ladakx.inertia.core.impl.config.ConfigServiceImpl;
import com.ladakx.inertia.core.impl.body.PhysicsBodiesServiceImpl;
import com.ladakx.inertia.api.body.PhysicsBodyType;
import com.ladakx.inertia.physics.body.impl.BlockPhysicsBody;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InertiaApiImpl implements InertiaApiProvider, InertiaApi {

    private static final ApiVersion API_VERSION = new ApiVersion(1, 4, 0);

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;
    private final RenderFactory renderFactory;
    private final JShapeFactory shapeFactory;

    private final RenderingService renderingService;
    private final ConfigService configService;
    private final CapabilityService capabilityService;
    private final DiagnosticsService diagnosticsService;
    private final ServiceRegistryImpl serviceRegistry = new ServiceRegistryImpl();
    private final ExtensionRegistryImpl extensionRegistry = new ExtensionRegistryImpl(this, serviceRegistry);
    private final RenderModelRegistryService renderModelRegistry;
    private final PhysicsBodiesService physicsBodiesService;

    public InertiaApiImpl(@NotNull InertiaPlugin plugin,
                          @NotNull PhysicsWorldRegistry physicsWorldRegistry,
                          @NotNull ConfigurationService configurationService,
                          @NotNull JShapeFactory shapeFactory,
                          @NotNull NetworkEntityTracker networkEntityTracker,
                          @NotNull DiagnosticsService diagnosticsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.physicsWorldRegistry = Objects.requireNonNull(physicsWorldRegistry, "physicsWorldRegistry");
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService");
        this.renderFactory = plugin.getRenderFactory();
        this.shapeFactory = Objects.requireNonNull(shapeFactory, "shapeFactory");

        this.renderingService = new RenderingServiceImpl(renderFactory, networkEntityTracker);
        this.configService = new ConfigServiceImpl();
        this.capabilityService = new CapabilityServiceImpl(API_VERSION, resolveCapabilities());
        this.diagnosticsService = Objects.requireNonNull(diagnosticsService, "diagnosticsService");

        this.renderModelRegistry = new com.ladakx.inertia.core.impl.rendering.RenderModelRegistryServiceImpl();
        this.physicsBodiesService = new PhysicsBodiesServiceImpl(physicsWorldRegistry);

        // Platform services (stable entry points for new subsystems).
        serviceRegistry.register(plugin, com.ladakx.inertia.api.transport.TransportServices.TRANSPORTS,
                new com.ladakx.inertia.core.impl.transport.TransportServiceImpl(plugin, this));
        serviceRegistry.register(plugin, com.ladakx.inertia.api.jolt.JoltServices.JOLT,
                new com.ladakx.inertia.core.impl.jolt.JoltServiceImpl());
        serviceRegistry.register(plugin, RenderingModelServices.MODELS, renderModelRegistry);
        serviceRegistry.register(plugin, PhysicsBodyServices.BODIES, physicsBodiesService);

        plugin.getServer().getPluginManager().registerEvents(
                new AutoCleanupListener(serviceRegistry, extensionRegistry, renderModelRegistry, physicsBodiesService),
                plugin
        );
    }

    @Override
    public @NotNull InertiaApi getApi() {
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

        com.ladakx.inertia.physics.world.PhysicsWorld space = physicsWorldRegistry.getWorld(location.getWorld());
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
                return ApiResult.success(new BlockPhysicsBody(
                        space,
                        bodyId,
                        modelRegistry,
                        renderFactory,
                        shapeFactory,
                        initialPos,
                        initialRot,
                        space.getEventDispatcher()
                ));
            }
            InertiaLogger.warn("Cannot create body: Unsupported body type for ID '" + bodyId + "'.");
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_BODY_TYPE, "shape-invalid-params");
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
    public @Nullable PhysicsWorld getPhysicsWorld(@NotNull World world) {
        return physicsWorldRegistry.getWorld(world);
    }

    @Override
    public @NotNull Collection<PhysicsWorld> getAllPhysicsWorlds() {
        Collection<com.ladakx.inertia.physics.world.PhysicsWorld> worlds = physicsWorldRegistry.getAllWorlds();
        List<PhysicsWorld> result = new ArrayList<>(worlds.size());
        for (com.ladakx.inertia.physics.world.PhysicsWorld world : worlds) {
            result.add(world);
        }
        return result;
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

    @Override
    public @NotNull ApiVersion apiVersion() {
        return capabilityService.apiVersion();
    }

    @Override
    public @NotNull ServiceRegistry services() {
        return serviceRegistry;
    }

    @Override
    public @NotNull ExtensionRegistry extensions() {
        return extensionRegistry;
    }

    private @NotNull Set<ApiCapability> resolveCapabilities() {
        EnumSet<ApiCapability> supported = EnumSet.noneOf(ApiCapability.class);
        supported.add(ApiCapability.TRANSPORT_PLATFORM);
        supported.add(ApiCapability.JOLT_NATIVE_ACCESS);
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

    private static final class ServiceRegistryImpl implements ServiceRegistry {
        private record Entry(Plugin owner, Object implementation) {}

        private final Map<ServiceKey<?>, Entry> entries = new ConcurrentHashMap<>();

        @Override
        public <T> void register(@NotNull Plugin owner, @NotNull ServiceKey<T> key, @NotNull T implementation) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(implementation, "implementation");
            if (!key.type().isInstance(implementation)) {
                throw new IllegalArgumentException("Implementation is not an instance of " + key.type().getName());
            }
            Entry prev = entries.putIfAbsent(key, new Entry(owner, implementation));
            if (prev != null) {
                throw new IllegalStateException("Service already registered: " + key.id());
            }
        }

        @Override
        public <T> @Nullable T replace(@NotNull Plugin owner, @NotNull ServiceKey<T> key, @NotNull T implementation) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(implementation, "implementation");
            if (!key.type().isInstance(implementation)) {
                throw new IllegalArgumentException("Implementation is not an instance of " + key.type().getName());
            }
            Entry prev = entries.put(key, new Entry(owner, implementation));
            if (prev == null) return null;
            return (T) prev.implementation;
        }

        @Override
        public <T> @Nullable T get(@NotNull ServiceKey<T> key) {
            Objects.requireNonNull(key, "key");
            Entry entry = entries.get(key);
            if (entry == null) return null;
            return (T) entry.implementation;
        }

        @Override
        public boolean unregister(@NotNull ServiceKey<?> key) {
            Objects.requireNonNull(key, "key");
            return entries.remove(key) != null;
        }

        @Override
        public void unregisterAll(@NotNull Plugin owner) {
            Objects.requireNonNull(owner, "owner");
            entries.entrySet().removeIf(e -> e.getValue().owner.equals(owner));
        }
    }

    private static final class ExtensionRegistryImpl implements ExtensionRegistry {
        private record Registered(Plugin owner, InertiaExtension extension) {}

        private final InertiaApi api;
        private final ServiceRegistry services;
        private final Map<String, Registered> byId = new ConcurrentHashMap<>();
        private final Map<Plugin, List<String>> idsByOwner = new ConcurrentHashMap<>();

        private ExtensionRegistryImpl(InertiaApi api, ServiceRegistry services) {
            this.api = api;
            this.services = services;
        }

        @Override
        public @NotNull ExtensionHandle register(@NotNull Plugin owner, @NotNull InertiaExtension extension) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(extension, "extension");

            String id = Objects.requireNonNull(extension.id(), "extension.id()");
            if (id.isBlank()) {
                throw new IllegalArgumentException("extension.id() must not be blank");
            }
            if (!api.apiVersion().isAtLeast(extension.minimumApiVersion())) {
                throw new IllegalStateException("Extension '" + id + "' requires API >= " + extension.minimumApiVersion() + " (current " + api.apiVersion() + ")");
            }
            for (var cap : extension.requiredCapabilities()) {
                api.capabilities().require(cap);
            }

            Registered existing = byId.putIfAbsent(id, new Registered(owner, extension));
            if (existing != null) {
                throw new IllegalStateException("Extension already registered: " + id);
            }
            idsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(id);

            try {
                extension.onLoad(new ExtensionContext() {
                    @Override
                    public @NotNull InertiaApi api() {
                        return api;
                    }

                    @Override
                    public @NotNull Plugin owner() {
                        return owner;
                    }

                    @Override
                    public @NotNull ServiceRegistry services() {
                        return services;
                    }
                });
            } catch (Exception e) {
                unregisterInternal(id);
                throw new IllegalStateException("Failed to load extension: " + id, e);
            }

            return () -> unregisterInternal(id);
        }

        @Override
        public void unregisterAll(@NotNull Plugin owner) {
            Objects.requireNonNull(owner, "owner");
            List<String> ids = idsByOwner.remove(owner);
            if (ids == null) return;
            for (String id : ids) {
                unregisterInternal(id);
            }
        }

        private void unregisterInternal(@NotNull String id) {
            Registered reg = byId.remove(id);
            if (reg == null) return;
            try {
                reg.extension.onUnload();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class AutoCleanupListener implements Listener {
        private final ServiceRegistry services;
        private final ExtensionRegistry extensions;
        private final RenderModelRegistryService renderModels;
        private final PhysicsBodiesService bodies;

        private AutoCleanupListener(ServiceRegistry services,
                                    ExtensionRegistry extensions,
                                    RenderModelRegistryService renderModels,
                                    PhysicsBodiesService bodies) {
            this.services = services;
            this.extensions = extensions;
            this.renderModels = renderModels;
            this.bodies = bodies;
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            Plugin plugin = event.getPlugin();
            if (plugin.equals(InertiaPlugin.getInstance())) {
                return;
            }
            try {
                extensions.unregisterAll(plugin);
            } catch (Exception ignored) {
            }
            try {
                services.unregisterAll(plugin);
            } catch (Exception ignored) {
            }
            try {
                renderModels.unregisterAll(plugin);
            } catch (Exception ignored) {
            }
            try {
                bodies.destroyAll(plugin);
            } catch (Exception ignored) {
            }
        }
    }
}
