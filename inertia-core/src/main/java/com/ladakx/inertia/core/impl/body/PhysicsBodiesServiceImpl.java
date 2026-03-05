package com.ladakx.inertia.core.impl.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.body.PhysicsBodiesService;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.body.PhysicsBodyOwner;
import com.ladakx.inertia.api.capability.ApiCapabilities;
import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.events.PhysicsBodyPostSpawnEvent;
import com.ladakx.inertia.api.events.PhysicsBodyPreSpawnEvent;
import com.ladakx.inertia.api.physics.*;
import com.ladakx.inertia.api.world.PhysicsWorld;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.CustomPhysicsBody;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.factory.shape.ApiShapeConverter;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PhysicsBodiesServiceImpl implements PhysicsBodiesService {

    private static final int PERSISTENCE_VERSION = 1;

    private final ApiShapeConverter shapeConverter = new ApiShapeConverter();
    private final @NotNull PhysicsWorldRegistry worldRegistry;

    // Identity-based because bodyId can be user-defined and non-unique.
    private final Map<PhysicsBody, PersistentBodyTemplate> persistentTemplates =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public PhysicsBodiesServiceImpl(@NotNull PhysicsWorldRegistry worldRegistry) {
        this.worldRegistry = Objects.requireNonNull(worldRegistry, "worldRegistry");
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> spawn(@NotNull Plugin owner, @NotNull PhysicsWorld world, @NotNull PhysicsBodySpec spec) {
        return spawnInternal(owner, world, spec, false, null);
    }

    @Override
    public @NotNull ApiResult<PhysicsBody> spawnPersistent(@NotNull Plugin owner,
                                                           @NotNull PhysicsWorld world,
                                                           @NotNull PhysicsBodySpec spec,
                                                           @Nullable Map<String, String> customData) {
        return spawnInternal(owner, world, spec, true, customData);
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

    @Override
    public void setPersistentData(@NotNull Plugin owner,
                                  @NotNull PhysicsBody body,
                                  @Nullable Map<String, String> customData) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(body, "body");
        PhysicsBodyOwner bodyOwner = ownerOf(body);
        if (bodyOwner.kind() != PhysicsBodyOwner.Kind.PLUGIN || !owner.getName().equals(bodyOwner.id())) {
            throw new IllegalArgumentException("Body is not owned by plugin: " + owner.getName());
        }
        synchronized (persistentTemplates) {
            PersistentBodyTemplate template = persistentTemplates.get(body);
            if (template == null) {
                throw new IllegalArgumentException("Body is not tracked as persistent");
            }
            persistentTemplates.put(body, template.withCustomData(sanitizeCustomData(customData)));
        }
    }

    @Override
    public @NotNull Map<String, String> getPersistentData(@NotNull PhysicsBody body) {
        Objects.requireNonNull(body, "body");
        synchronized (persistentTemplates) {
            PersistentBodyTemplate template = persistentTemplates.get(body);
            return template == null ? Map.of() : template.customData();
        }
    }

    @Override
    public @NotNull ApiResult<Integer> saveOwned(@NotNull Plugin owner, @NotNull String relativePath) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(relativePath, "relativePath");

        File targetFile;
        try {
            targetFile = resolveOwnerFile(owner, relativePath);
        } catch (Exception e) {
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", PERSISTENCE_VERSION);

        List<Map<String, Object>> bodyList = new ArrayList<>();
        synchronized (persistentTemplates) {
            Iterator<Map.Entry<PhysicsBody, PersistentBodyTemplate>> it = persistentTemplates.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<PhysicsBody, PersistentBodyTemplate> entry = it.next();
                PhysicsBody body = entry.getKey();
                PersistentBodyTemplate template = entry.getValue();

                if (!body.isValid()) {
                    it.remove();
                    continue;
                }
                if (!template.ownerName().equals(owner.getName())) {
                    continue;
                }

                Location location = body.getLocation();
                if (location.getWorld() == null) {
                    continue;
                }

                Quaternionf rotation = extractBodyRotation(body, location);
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("world", location.getWorld().getName());
                serialized.put("body-id", body.getBodyId());
                serialized.put("motion", body.getMotionType().name());
                serialized.put("location", vec3(location.getX(), location.getY(), location.getZ()));
                serialized.put("rotation", quat(rotation));
                serialized.put("mass", template.spec().mass());
                serialized.put("friction", body.getFriction());
                serialized.put("restitution", body.getRestitution());
                serialized.put("gravity-factor", body.getGravityFactor());
                serialized.put("linear-damping", template.spec().linearDamping());
                serialized.put("angular-damping", template.spec().angularDamping());
                serialized.put("object-layer", template.spec().objectLayer());
                serialized.put("shape", serializeShape(template.spec().shape()));
                serialized.put("velocity", Map.of(
                        "linear", vector(body.getLinearVelocity()),
                        "angular", vector(body.getAngularVelocity())
                ));
                serialized.put("custom-data", template.customData());
                bodyList.add(serialized);
            }
        }

        yaml.set("bodies", bodyList);

        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(targetFile);
            return ApiResult.success(bodyList.size());
        } catch (IOException e) {
            InertiaLogger.error("Failed to save persistent plugin bodies to " + targetFile.getAbsolutePath(), e);
            return ApiResult.failure(ApiErrorCode.INTERNAL_ERROR, "error-occurred");
        }
    }

    @Override
    public @NotNull ApiResult<Integer> loadOwned(@NotNull Plugin owner, @NotNull String relativePath) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(relativePath, "relativePath");

        File targetFile;
        try {
            targetFile = resolveOwnerFile(owner, relativePath);
        } catch (Exception e) {
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }

        if (!targetFile.exists()) {
            return ApiResult.success(0);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(targetFile);
        List<?> rawBodies = yaml.getList("bodies");
        if (rawBodies == null || rawBodies.isEmpty()) {
            return ApiResult.success(0);
        }

        int loaded = 0;
        for (Object entry : rawBodies) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            LoadedBodyState state;
            try {
                state = deserializeBodyState(map);
            } catch (Exception e) {
                InertiaLogger.warn("Skipping invalid persisted body entry: " + e.getMessage());
                continue;
            }

            org.bukkit.World bukkitWorld = Bukkit.getWorld(state.worldName());
            if (bukkitWorld == null) {
                continue;
            }
            com.ladakx.inertia.physics.world.PhysicsWorld world = worldRegistry.getWorld(bukkitWorld);
            if (world == null) {
                continue;
            }

            Location location = new Location(bukkitWorld, state.x(), state.y(), state.z());
            PhysicsBodySpec.Builder builder = PhysicsBodySpec.builder(location, state.shape())
                    .rotation(state.rotation())
                    .motionType(state.motionType())
                    .bodyId(state.bodyId())
                    .mass(state.mass())
                    .friction(state.friction())
                    .restitution(state.restitution())
                    .gravityFactor(state.gravityFactor())
                    .linearDamping(state.linearDamping())
                    .angularDamping(state.angularDamping());

            if (state.objectLayer() != null) {
                builder.objectLayer(state.objectLayer());
            }

            ApiResult<PhysicsBody> spawnResult = spawnPersistent(owner, world, builder.build(), state.customData());
            if (!spawnResult.isSuccess() || spawnResult.getValue() == null) {
                continue;
            }

            PhysicsBody body = spawnResult.getValue();
            body.setLinearVelocity(state.linearVelocity());
            body.setAngularVelocity(state.angularVelocity());
            loaded++;
        }

        return ApiResult.success(loaded);
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
        synchronized (persistentTemplates) {
            persistentTemplates.entrySet().removeIf(e -> ownerName.equals(e.getValue().ownerName()));
        }
    }

    private @NotNull ApiResult<PhysicsBody> spawnInternal(@NotNull Plugin owner,
                                                           @NotNull PhysicsWorld world,
                                                           @NotNull PhysicsBodySpec spec,
                                                           boolean persistent,
                                                           @Nullable Map<String, String> customData) {
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

            ApiResult<PhysicsBody> result = spawnJolt(owner, space, settings, spec.bodyId());
            if (persistent && result.isSuccess() && result.getValue() != null) {
                persistentTemplates.put(result.getValue(), new PersistentBodyTemplate(
                        owner.getName(),
                        spec,
                        sanitizeCustomData(customData)
                ));
            }
            return result;
        } catch (Exception e) {
            InertiaLogger.error("Failed to spawn plugin body in world " + space.getWorldBukkit().getName(), e);
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "shape-invalid-params");
        }
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

    private static @NotNull com.ladakx.inertia.physics.world.PhysicsWorld requireSpace(@NotNull PhysicsWorld world) {
        if (world instanceof com.ladakx.inertia.physics.world.PhysicsWorld space) {
            return space;
        }
        throw new IllegalArgumentException("Unsupported PhysicsWorld implementation: " + world.getClass().getName());
    }

    private static @NotNull Map<String, String> sanitizeCustomData(@Nullable Map<String, String> customData) {
        if (customData == null || customData.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : customData.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static @NotNull Quaternionf extractBodyRotation(@NotNull PhysicsBody body, @NotNull Location location) {
        if (body instanceof AbstractPhysicsBody internal) {
            Quat q = internal.getBody().getRotation();
            return new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW());
        }
        float yawRad = (float) Math.toRadians(-location.getYaw());
        float pitchRad = (float) Math.toRadians(location.getPitch());
        return new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
    }

    private static @NotNull File resolveOwnerFile(@NotNull Plugin owner, @NotNull String relativePath) throws IOException {
        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("relativePath must be a safe relative path");
        }
        File dataFolder = owner.getDataFolder();
        File targetFile = new File(dataFolder, normalized);
        String basePath = dataFolder.getCanonicalPath();
        String targetPath = targetFile.getCanonicalPath();
        if (!targetPath.startsWith(basePath + File.separator) && !targetPath.equals(basePath)) {
            throw new IllegalArgumentException("relativePath escapes plugin data folder");
        }
        return targetFile;
    }

    private static @NotNull Map<String, Object> vec3(double x, double y, double z) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        return map;
    }

    private static @NotNull Map<String, Object> vector(@NotNull Vector v) {
        return vec3(v.getX(), v.getY(), v.getZ());
    }

    private static @NotNull Map<String, Object> quat(@NotNull Quaternionf q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", q.x);
        map.put("y", q.y);
        map.put("z", q.z);
        map.put("w", q.w);
        return map;
    }

    private static @NotNull Map<String, Object> serializeShape(@NotNull PhysicsShape shape) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", shape.kind().name());
        if (shape instanceof BoxShape box) {
            map.put("half-x", box.halfX());
            map.put("half-y", box.halfY());
            map.put("half-z", box.halfZ());
            map.put("convex-radius", box.convexRadius());
        } else if (shape instanceof SphereShape sphere) {
            map.put("radius", sphere.radius());
        } else if (shape instanceof CapsuleShape capsule) {
            map.put("height", capsule.height());
            map.put("radius", capsule.radius());
        } else if (shape instanceof CylinderShape cylinder) {
            map.put("height", cylinder.height());
            map.put("radius", cylinder.radius());
            map.put("convex-radius", cylinder.convexRadius());
        } else if (shape instanceof TaperedCapsuleShape taperedCapsule) {
            map.put("height", taperedCapsule.height());
            map.put("top-radius", taperedCapsule.topRadius());
            map.put("bottom-radius", taperedCapsule.bottomRadius());
        } else if (shape instanceof TaperedCylinderShape taperedCylinder) {
            map.put("height", taperedCylinder.height());
            map.put("top-radius", taperedCylinder.topRadius());
            map.put("bottom-radius", taperedCylinder.bottomRadius());
            map.put("convex-radius", taperedCylinder.convexRadius());
        } else if (shape instanceof ConvexHullShape hull) {
            List<Map<String, Object>> points = new ArrayList<>(hull.points().size());
            for (Vector3f p : hull.points()) {
                points.add(vec3(p.x, p.y, p.z));
            }
            map.put("points", points);
            map.put("convex-radius", hull.convexRadius());
        } else if (shape instanceof CompoundShape compound) {
            List<Map<String, Object>> children = new ArrayList<>(compound.children().size());
            for (ShapeInstance child : compound.children()) {
                Map<String, Object> childMap = new LinkedHashMap<>();
                childMap.put("shape", serializeShape(child.shape()));
                childMap.put("position", vec3(child.position().x, child.position().y, child.position().z));
                childMap.put("rotation", quat(child.rotation()));
                if (child.centerOfMassOffset() != null) {
                    childMap.put("center-of-mass-offset",
                            vec3(child.centerOfMassOffset().x, child.centerOfMassOffset().y, child.centerOfMassOffset().z));
                }
                children.add(childMap);
            }
            map.put("children", children);
        } else if (shape instanceof CustomShape custom) {
            map.put("type", custom.type());
            map.put("params", custom.params());
        } else {
            throw new IllegalArgumentException("Unsupported shape kind: " + shape.kind());
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull PhysicsShape deserializeShape(@NotNull Map<?, ?> raw) {
        String kindRaw = String.valueOf(raw.get("kind"));
        PhysicsShape.Kind kind = PhysicsShape.Kind.valueOf(kindRaw.toUpperCase(Locale.ROOT));

        return switch (kind) {
            case BOX -> new BoxShape(
                    asFloat(raw.get("half-x")),
                    asFloat(raw.get("half-y")),
                    asFloat(raw.get("half-z")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case SPHERE -> new SphereShape(asFloat(raw.get("radius")));
            case CAPSULE -> new CapsuleShape(asFloat(raw.get("height")), asFloat(raw.get("radius")));
            case CYLINDER -> new CylinderShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("radius")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case TAPERED_CAPSULE -> new TaperedCapsuleShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("top-radius")),
                    asFloat(raw.get("bottom-radius"))
            );
            case TAPERED_CYLINDER -> new TaperedCylinderShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("top-radius")),
                    asFloat(raw.get("bottom-radius")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case CONVEX_HULL -> {
                List<?> list = (List<?>) raw.get("points");
                List<Vector3f> points = new ArrayList<>();
                if (list != null) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> pointMap) {
                            points.add(new Vector3f(
                                    asFloat(pointMap.get("x")),
                                    asFloat(pointMap.get("y")),
                                    asFloat(pointMap.get("z"))
                            ));
                        }
                    }
                }
                yield new ConvexHullShape(points, asFloat(valueOrDefault(raw, "convex-radius", -1f)));
            }
            case COMPOUND -> {
                List<?> list = (List<?>) raw.get("children");
                List<ShapeInstance> children = new ArrayList<>();
                if (list != null) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> childMap)) {
                            continue;
                        }
                        Map<?, ?> shapeMap = (Map<?, ?>) childMap.get("shape");
                        Map<?, ?> posMap = (Map<?, ?>) childMap.get("position");
                        Map<?, ?> rotMap = (Map<?, ?>) childMap.get("rotation");
                        Map<?, ?> comMap = (Map<?, ?>) childMap.get("center-of-mass-offset");
                        Vector3f com = comMap == null ? null : new Vector3f(
                                asFloat(comMap.get("x")),
                                asFloat(comMap.get("y")),
                                asFloat(comMap.get("z"))
                        );
                        children.add(new ShapeInstance(
                                deserializeShape(Objects.requireNonNull(shapeMap, "child shape")),
                                new Vector3f(asFloat(posMap.get("x")), asFloat(posMap.get("y")), asFloat(posMap.get("z"))),
                                new Quaternionf(asFloat(rotMap.get("x")), asFloat(rotMap.get("y")), asFloat(rotMap.get("z")), asFloat(rotMap.get("w"))),
                                com
                        ));
                    }
                }
                yield new CompoundShape(children);
            }
            case CUSTOM -> {
                String type = String.valueOf(raw.get("type"));
                Map<String, Object> params = new LinkedHashMap<>();
                Object p = raw.get("params");
                if (p instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (e.getKey() != null) {
                            params.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                }
                yield new CustomShape(type, params);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static @NotNull LoadedBodyState deserializeBodyState(@NotNull Map<?, ?> map) {
        String world = String.valueOf(map.get("world"));
        String bodyId = (String) map.get("body-id");
        MotionType motionType = MotionType.valueOf(String.valueOf(valueOrDefault(map, "motion", MotionType.DYNAMIC.name())).toUpperCase(Locale.ROOT));

        Map<?, ?> loc = (Map<?, ?>) map.get("location");
        Map<?, ?> rot = (Map<?, ?>) map.get("rotation");
        Map<?, ?> shapeMap = (Map<?, ?>) map.get("shape");

        Map<?, ?> velocity = (Map<?, ?>) map.get("velocity");
        Map<?, ?> linear = velocity == null ? null : (Map<?, ?>) velocity.get("linear");
        Map<?, ?> angular = velocity == null ? null : (Map<?, ?>) velocity.get("angular");

        Map<String, String> customData = new LinkedHashMap<>();
        Object rawCustomData = map.get("custom-data");
        if (rawCustomData instanceof Map<?, ?> customMap) {
            for (Map.Entry<?, ?> e : customMap.entrySet()) {
                if (e.getKey() == null) continue;
                customData.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }

        Integer objectLayer = map.get("object-layer") == null ? null : asInt(map.get("object-layer"));

        return new LoadedBodyState(
                world,
                bodyId,
                asDouble(loc.get("x")),
                asDouble(loc.get("y")),
                asDouble(loc.get("z")),
                new Quaternionf(asFloat(rot.get("x")), asFloat(rot.get("y")), asFloat(rot.get("z")), asFloat(rot.get("w"))),
                motionType,
                asFloat(valueOrDefault(map, "mass", 1f)),
                asFloat(valueOrDefault(map, "friction", 0.2f)),
                asFloat(valueOrDefault(map, "restitution", 0f)),
                asFloat(valueOrDefault(map, "gravity-factor", 1f)),
                asFloat(valueOrDefault(map, "linear-damping", 0f)),
                asFloat(valueOrDefault(map, "angular-damping", 0f)),
                objectLayer,
                deserializeShape(Objects.requireNonNull(shapeMap, "shape")),
                linear == null ? new Vector() : new Vector(asDouble(linear.get("x")), asDouble(linear.get("y")), asDouble(linear.get("z"))),
                angular == null ? new Vector() : new Vector(asDouble(angular.get("x")), asDouble(angular.get("y")), asDouble(angular.get("z"))),
                Collections.unmodifiableMap(customData)
        );
    }

    private static @Nullable Object valueOrDefault(@NotNull Map<?, ?> map, @NotNull String key, @Nullable Object defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : value;
    }

    private static float asFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null) return 0f;
        return Float.parseFloat(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) return 0d;
        return Double.parseDouble(String.valueOf(value));
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) return 0;
        return Integer.parseInt(String.valueOf(value));
    }

    private record PersistentBodyTemplate(
            String ownerName,
            PhysicsBodySpec spec,
            Map<String, String> customData
    ) {
        private PersistentBodyTemplate {
            Objects.requireNonNull(ownerName, "ownerName");
            Objects.requireNonNull(spec, "spec");
            customData = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(customData, "customData")));
        }

        private @NotNull PersistentBodyTemplate withCustomData(@NotNull Map<String, String> newData) {
            return new PersistentBodyTemplate(ownerName, spec, newData);
        }
    }

    private record LoadedBodyState(
            String worldName,
            String bodyId,
            double x,
            double y,
            double z,
            Quaternionf rotation,
            MotionType motionType,
            float mass,
            float friction,
            float restitution,
            float gravityFactor,
            float linearDamping,
            float angularDamping,
            Integer objectLayer,
            PhysicsShape shape,
            Vector linearVelocity,
            Vector angularVelocity,
            Map<String, String> customData
    ) {
    }
}
