package com.ladakx.inertia.core.impl.transport;

import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApi;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.transport.*;
import com.ladakx.inertia.api.transport.events.*;
import com.ladakx.inertia.common.logging.InertiaLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TransportServiceImpl implements TransportService, Listener {

    private record RegisteredType(Plugin owner, TransportType type) {}

    private record TransportEntry(TransportImpl transport,
                                  Plugin owner,
                                  TransportType type,
                                  @Nullable TransportController controller,
                                  List<AutoCloseable> resources) {}

    private final Plugin platformPlugin;
    private final InertiaApi api;
    private final ConcurrentMap<TransportTypeKey, RegisteredType> types = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TransportEntry> transports = new ConcurrentHashMap<>();
    private final ConcurrentMap<Plugin, Set<UUID>> transportIdsByOwner = new ConcurrentHashMap<>();
    private final ConcurrentMap<TransportTypeKey, AtomicBoolean> typeInUseHint = new ConcurrentHashMap<>();

    private final BukkitTask tickTask;
    private volatile long tickNumber = 0L;

    public TransportServiceImpl(@NotNull Plugin platformPlugin, @NotNull InertiaApi api) {
        this.platformPlugin = Objects.requireNonNull(platformPlugin, "platformPlugin");
        this.api = Objects.requireNonNull(api, "api");

        Bukkit.getPluginManager().registerEvents(this, platformPlugin);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(platformPlugin, this::tick, 1L, 1L);
    }

    @Override
    public @NotNull ApiResult<TransportTypeHandle> registerType(@NotNull Plugin owner, @NotNull TransportType type) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");

        TransportTypeDescriptor descriptor = Objects.requireNonNull(type.descriptor(), "type.descriptor()");
        TransportTypeKey key = Objects.requireNonNull(descriptor.key(), "descriptor.key()");

        RegisteredType prev = types.putIfAbsent(key, new RegisteredType(owner, type));
        if (prev != null) {
            return ApiResult.failure(ApiErrorCode.TRANSPORT_TYPE_ALREADY_REGISTERED, "transport.type-already-registered");
        }

        try {
            Bukkit.getPluginManager().callEvent(new TransportTypeRegisteredEvent(
                    new TransportTypeRegisteredPayload(
                            TransportEventPayload.SCHEMA_VERSION_V1,
                            key.asString(),
                            descriptor.displayName(),
                            owner.getName()
                    )
            ));
        } catch (Exception ignored) {
        }

        return ApiResult.success(new TransportTypeHandle() {
            @Override
            public @NotNull TransportTypeKey key() {
                return key;
            }

            @Override
            public void close() {
                unregisterType(key);
            }
        });
    }

    @Override
    public @NotNull Collection<TransportTypeDescriptor> getRegisteredTypes() {
        ArrayList<TransportTypeDescriptor> result = new ArrayList<>(types.size());
        for (RegisteredType reg : types.values()) {
            try {
                result.add(reg.type.descriptor());
            } catch (Exception ignored) {
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable TransportTypeDescriptor getType(@NotNull TransportTypeKey key) {
        Objects.requireNonNull(key, "key");
        RegisteredType reg = types.get(key);
        if (reg == null) return null;
        return reg.type.descriptor();
    }

    @Override
    public @NotNull ApiResult<Void> unregisterType(@NotNull TransportTypeKey key) {
        Objects.requireNonNull(key, "key");
        if (!types.containsKey(key)) {
            return ApiResult.failure(ApiErrorCode.TRANSPORT_TYPE_NOT_FOUND, "transport.type-not-found");
        }

        AtomicBoolean inUse = typeInUseHint.get(key);
        if (inUse != null && inUse.get()) {
            // Slow path: validate by scanning transports.
            for (TransportEntry entry : transports.values()) {
                if (entry.transport.type().equals(key)) {
                    return ApiResult.failure(ApiErrorCode.TRANSPORT_TYPE_IN_USE, "transport.type-in-use");
                }
            }
        }

        types.remove(key);
        typeInUseHint.remove(key);
        return ApiResult.success(null);
    }

    @Override
    public @NotNull ApiResult<Transport> spawn(@NotNull Plugin owner, @NotNull TransportSpawnRequest request) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");

        Location location = request.location();
        World world = location.getWorld();
        if (world == null || api.getPhysicsWorld(world) == null) {
            return ApiResult.failure(ApiErrorCode.WORLD_NOT_SIMULATED, "transport.world-not-simulated");
        }

        TransportTypeKey typeKey = request.type();
        RegisteredType reg = types.get(typeKey);
        if (reg == null) {
            return ApiResult.failure(ApiErrorCode.TRANSPORT_TYPE_NOT_FOUND, "transport.type-not-found");
        }

        TransportBuildResult built;
        try {
            ApiResult<TransportBuildResult> result = reg.type.build(new TransportBuildContext() {
                @Override
                public @NotNull InertiaApi api() {
                    return api;
                }

                @Override
                public @NotNull Plugin typeOwner() {
                    return reg.owner;
                }

                @Override
                public @NotNull Plugin instanceOwner() {
                    return owner;
                }

                @Override
                public @NotNull TransportSpawnRequest request() {
                    return request;
                }
            });
            if (!result.isSuccess() || result.getValue() == null) {
                ApiErrorCode code = result.getErrorCode() == null ? ApiErrorCode.TRANSPORT_BUILD_FAILED : result.getErrorCode();
                String messageKey = result.getMessageKey() == null ? "transport.build-failed" : result.getMessageKey();
                return ApiResult.failure(code, messageKey);
            }
            built = result.getValue();
        } catch (Exception e) {
            InertiaLogger.warn("Transport build failed for type '" + typeKey.asString() + "': " + e.getMessage());
            return ApiResult.failure(ApiErrorCode.TRANSPORT_BUILD_FAILED, "transport.build-failed");
        }

        PhysicsBody primary = Objects.requireNonNull(built.primaryBody(), "built.primaryBody()");
        if (!primary.isValid()) {
            safeDestroyBuild(built);
            return ApiResult.failure(ApiErrorCode.TRANSPORT_BUILD_FAILED, "transport.invalid-primary-body");
        }

        UUID transportId = UUID.randomUUID();
        TransportImpl transport = new TransportImpl(
                transportId,
                typeKey,
                owner,
                primary,
                built.bodies(),
                Objects.requireNonNull(world).getUID(),
                world.getName(),
                this
        );

        TransportController controller = built.controller();
        List<AutoCloseable> resources = built.resources();
        transports.put(transportId, new TransportEntry(transport, owner, reg.type, controller, resources));
        transportIdsByOwner.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(transportId);
        typeInUseHint.computeIfAbsent(typeKey, k -> new AtomicBoolean(false)).set(true);

        try {
            Bukkit.getPluginManager().callEvent(new TransportSpawnEvent(new TransportSpawnPayload(
                    TransportEventPayload.SCHEMA_VERSION_V1,
                    transportId,
                    typeKey.asString(),
                    Objects.requireNonNull(world).getUID(),
                    world.getName(),
                    owner.getName()
            )));
        } catch (Exception ignored) {
        }

        return ApiResult.success(transport);
    }

    @Override
    public @Nullable Transport get(@NotNull UUID transportId) {
        Objects.requireNonNull(transportId, "transportId");
        TransportEntry entry = transports.get(transportId);
        return entry == null ? null : entry.transport;
    }

    @Override
    public @NotNull Collection<Transport> getAll() {
        ArrayList<Transport> out = new ArrayList<>(transports.size());
        for (TransportEntry entry : transports.values()) {
            out.add(entry.transport);
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public @NotNull ApiResult<Void> destroy(@NotNull UUID transportId) {
        Objects.requireNonNull(transportId, "transportId");
        boolean ok = destroyInternal(transportId, TransportDestroyReason.EXPLICIT);
        if (!ok) {
            return ApiResult.failure(ApiErrorCode.TRANSPORT_NOT_FOUND, "transport.not-found");
        }
        return ApiResult.success(null);
    }

    @Override
    public void destroyAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        Set<UUID> ids = transportIdsByOwner.remove(owner);
        if (ids == null || ids.isEmpty()) return;
        for (UUID id : ids) {
            destroyInternal(id, TransportDestroyReason.PLUGIN_DISABLE);
        }
    }

    private void tick() {
        tickNumber++;
        TransportTickContext ctx = new TransportTickContext(tickNumber, 1.0f / 20.0f);
        for (TransportEntry entry : transports.values()) {
            TransportController controller = entry.controller;
            if (controller == null) continue;
            TransportImpl transport = entry.transport;
            if (!transport.isValid()) {
                destroyInternal(transport.id(), TransportDestroyReason.ERROR);
                continue;
            }
            try {
                controller.tick(transport, ctx);
            } catch (Exception e) {
                InertiaLogger.warn("Transport controller error for '" + transport.type().asString() + "' (" + transport.id() + "): " + e.getMessage());
                destroyInternal(transport.id(), TransportDestroyReason.ERROR);
            }
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabled = event.getPlugin();
        if (disabled.equals(platformPlugin)) {
            shutdown();
            return;
        }

        destroyAll(disabled);
        // Unregister types owned by this plugin (best-effort; ignores in-use types).
        for (var e : types.entrySet()) {
            if (e.getValue().owner.equals(disabled)) {
                unregisterType(e.getKey());
            }
        }
    }

    private void shutdown() {
        try {
            tickTask.cancel();
        } catch (Exception ignored) {
        }
        for (UUID id : new ArrayList<>(transports.keySet())) {
            destroyInternal(id, TransportDestroyReason.PLATFORM_SHUTDOWN);
        }
        types.clear();
        transportIdsByOwner.clear();
        typeInUseHint.clear();
    }

    private boolean destroyInternal(@NotNull UUID id, @NotNull TransportDestroyReason reason) {
        TransportEntry entry = transports.remove(id);
        if (entry == null) return false;

        transportIdsByOwner.computeIfPresent(entry.owner, (k, set) -> {
            set.remove(id);
            return set.isEmpty() ? null : set;
        });

        try {
            entry.transport.invalidate();
        } catch (Exception ignored) {
        }

        // Close resources first (may depend on bodies still existing).
        for (AutoCloseable c : entry.resources) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }

        // Destroy bodies last.
        for (PhysicsBody body : entry.transport.bodies()) {
            try {
                if (body.isValid()) {
                    body.destroy();
                }
            } catch (Exception ignored) {
            }
        }

        try {
            Bukkit.getPluginManager().callEvent(new TransportDestroyEvent(new TransportDestroyPayload(
                    TransportEventPayload.SCHEMA_VERSION_V1,
                    id,
                    entry.transport.type().asString(),
                    entry.transport.worldId,
                    entry.transport.worldName,
                    entry.owner.getName(),
                    reason
            )));
        } catch (Exception ignored) {
        }

        // Maintain in-use hint.
        TransportTypeKey typeKey = entry.transport.type();
        AtomicBoolean hint = typeInUseHint.get(typeKey);
        if (hint != null) {
            boolean stillInUse = false;
            for (TransportEntry e : transports.values()) {
                if (e.transport.type().equals(typeKey)) {
                    stillInUse = true;
                    break;
                }
            }
            hint.set(stillInUse);
        }

        return true;
    }

    private void safeDestroyBuild(@NotNull TransportBuildResult built) {
        for (AutoCloseable c : built.resources()) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
        for (PhysicsBody b : built.bodies()) {
            try {
                if (b.isValid()) b.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class TransportImpl implements Transport {
        private final UUID id;
        private final TransportTypeKey type;
        private final Plugin owner;
        private final PhysicsBody primaryBody;
        private final List<PhysicsBody> bodies;
        private final UUID worldId;
        private final String worldName;
        private final TransportServiceImpl service;
        private final AtomicBoolean valid = new AtomicBoolean(true);

        private TransportImpl(@NotNull UUID id,
                              @NotNull TransportTypeKey type,
                              @NotNull Plugin owner,
                              @NotNull PhysicsBody primaryBody,
                              @NotNull List<PhysicsBody> bodies,
                              @NotNull UUID worldId,
                              @NotNull String worldName,
                              @NotNull TransportServiceImpl service) {
            this.id = Objects.requireNonNull(id, "id");
            this.type = Objects.requireNonNull(type, "type");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.primaryBody = Objects.requireNonNull(primaryBody, "primaryBody");
            this.bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.worldName = Objects.requireNonNull(worldName, "worldName");
            this.service = Objects.requireNonNull(service, "service");
        }

        @Override
        public @NotNull UUID id() {
            return id;
        }

        @Override
        public @NotNull TransportTypeKey type() {
            return type;
        }

        @Override
        public @NotNull Plugin owner() {
            return owner;
        }

        @Override
        public @NotNull PhysicsBody primaryBody() {
            return primaryBody;
        }

        @Override
        public @NotNull List<PhysicsBody> bodies() {
            return bodies;
        }

        @Override
        public boolean isValid() {
            if (!valid.get()) return false;
            if (!primaryBody.isValid()) return false;
            return true;
        }

        @Override
        public void destroy() {
            service.destroyInternal(id, TransportDestroyReason.EXPLICIT);
        }

        private void invalidate() {
            valid.set(false);
        }
    }
}
