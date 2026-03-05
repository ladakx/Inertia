package com.ladakx.inertia.core.impl.transport;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;
import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.body.PhysicsBodiesService;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.transport.*;
import com.ladakx.inertia.api.transport.events.TransportEventPayload;
import com.ladakx.inertia.api.transport.events.TransportInputAppliedEvent;
import com.ladakx.inertia.api.transport.events.TransportInputPayload;
import com.ladakx.inertia.api.transport.events.TransportLifecyclePayload;
import com.ladakx.inertia.api.transport.events.TransportPostDestroyEvent;
import com.ladakx.inertia.api.transport.events.TransportPostSpawnEvent;
import com.ladakx.inertia.api.transport.events.TransportPreDestroyEvent;
import com.ladakx.inertia.api.transport.events.TransportPreSpawnEvent;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportServiceImpl implements TransportService, TransportAdvancedService, TransportNativeService {

    private static final int PERSISTENCE_VERSION = 1;

    private final @NotNull PhysicsWorldRegistry worldRegistry;
    private final @NotNull PhysicsBodiesService physicsBodies;

    private final Map<String, RuntimeTransport> byId = new ConcurrentHashMap<>();

    public TransportServiceImpl(@NotNull PhysicsWorldRegistry worldRegistry,
                                @NotNull PhysicsBodiesService physicsBodies) {
        this.worldRegistry = Objects.requireNonNull(worldRegistry, "worldRegistry");
        this.physicsBodies = Objects.requireNonNull(physicsBodies, "physicsBodies");
    }

    @Override
    public @NotNull ApiResult<TransportHandle> spawn(@NotNull Plugin owner,
                                                     @NotNull World world,
                                                     @NotNull TransportSpec spec,
                                                     @Nullable Map<String, String> customData) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spec, "spec");

        com.ladakx.inertia.physics.world.PhysicsWorld physicsWorld = worldRegistry.getWorld(world);
        if (physicsWorld == null) {
            return ApiResult.failure(ApiErrorCode.WORLD_NOT_SIMULATED, "not-for-this-world");
        }

        TransportId id = spec.id() == null ? TransportId.random() : spec.id();
        if (byId.containsKey(id.value())) {
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "error-occurred");
        }

        Map<String, String> sanitizedCustomData = sanitizeCustomData(customData);
        TransportLifecyclePayload lifecyclePayload = lifecyclePayload(id, owner.getName(), world, spec.type());
        TransportPreSpawnEvent preSpawnEvent = new TransportPreSpawnEvent(lifecyclePayload, spec, sanitizedCustomData);
        Bukkit.getPluginManager().callEvent(preSpawnEvent);
        if (preSpawnEvent.isCancelled()) {
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "spawn-cancelled");
        }

        ApiResult<PhysicsBody> chassisResult = physicsBodies.spawnPersistent(owner, physicsWorld, spec.chassis().bodySpec(), sanitizedCustomData);
        if (!chassisResult.isSuccess() || chassisResult.getValue() == null) {
            return ApiResult.failure(
                    chassisResult.getErrorCode() == null ? ApiErrorCode.INTERNAL_ERROR : chassisResult.getErrorCode(),
                    chassisResult.getMessageKey() == null ? "error-occurred" : chassisResult.getMessageKey()
            );
        }

        PhysicsBody chassis = chassisResult.getValue();
        if (!(chassis instanceof AbstractPhysicsBody internalBody)) {
            try {
                chassis.destroy();
            } catch (Exception ignored) {
            }
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "error-occurred");
        }

        VehicleConstraint vehicleConstraint;
        try {
            vehicleConstraint = createVehicleConstraint(spec, internalBody);
            physicsWorld.addConstraint(vehicleConstraint);
            VehicleStepListener stepListener = vehicleConstraint.getStepListener();
            if (stepListener != null) {
                physicsWorld.getPhysicsSystem().addStepListener(stepListener);
            }
        } catch (Exception e) {
            try {
                chassis.destroy();
            } catch (Exception ignored) {
            }
            InertiaLogger.error("Failed to create transport constraint", e);
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "shape-invalid-params");
        }

        RuntimeTransport runtime = new RuntimeTransport(
                id,
                owner.getName(),
                physicsWorld,
                spec,
                chassis,
                vehicleConstraint,
                TransportInput.idle(),
                null,
                sanitizedCustomData
        );
        byId.put(id.value(), runtime);
        TransportHandleImpl handle = new TransportHandleImpl(id, runtime);
        Bukkit.getPluginManager().callEvent(new TransportPostSpawnEvent(lifecyclePayload, handle));
        return ApiResult.success(handle);
    }

    @Override
    public boolean destroy(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.remove(id.value());
        if (runtime == null) {
            return false;
        }
        safeDestroy(runtime);
        return true;
    }

    @Override
    public void destroyAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        String ownerName = owner.getName();
        List<RuntimeTransport> toDestroy = new ArrayList<>();
        for (Map.Entry<String, RuntimeTransport> entry : byId.entrySet()) {
            RuntimeTransport runtime = entry.getValue();
            if (runtime.ownerName().equals(ownerName) && byId.remove(entry.getKey(), runtime)) {
                toDestroy.add(runtime);
            }
        }
        for (RuntimeTransport runtime : toDestroy) {
            safeDestroy(runtime);
        }
    }

    @Override
    public boolean setInput(@NotNull TransportId id, @NotNull TransportInput input) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return false;
        }
        applyInput(runtime, input);
        byId.computeIfPresent(id.value(), (k, existing) -> existing.withInput(input, null));
        dispatchInputApplied(runtime, input, null);
        return true;
    }

    @Override
    public boolean setTrackedInput(@NotNull TransportId id, @NotNull TrackedInput input) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(input, "input");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return false;
        }
        if (runtime.spec().type() != TransportType.TRACKED) {
            return false;
        }
        applyTrackedInput(runtime, input);
        TransportInput compatInput = trackedToCompatInput(input);
        byId.computeIfPresent(id.value(), (k, existing) -> existing.withInput(compatInput, input));
        dispatchInputApplied(runtime, compatInput, input);
        return true;
    }

    @Override
    public @Nullable TransportState getState(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return null;
        }
        PhysicsBody chassis = runtime.chassis();
        if (!chassis.isValid()) {
            return null;
        }

        Vector linear = chassis.getLinearVelocity();
        Vector angular = chassis.getAngularVelocity();
        double speedMps = linear.length();
        double speedKmh = speedMps * 3.6d;

        float rpm = 0f;
        int gear = 0;
        boolean grounded = false;

        VehicleController controller = runtime.constraint().getController();
        if (controller instanceof WheeledVehicleController wheeled) {
            VehicleEngine engine = wheeled.getEngine();
            VehicleTransmission transmission = wheeled.getTransmission();
            rpm = engine.getCurrentRpm();
            gear = transmission.getCurrentGear();
        } else if (controller instanceof TrackedVehicleController tracked) {
            rpm = tracked.getEngine().getCurrentRpm();
        }

        int wheels = runtime.constraint().countWheels();
        for (int i = 0; i < wheels; i++) {
            Wheel wheel = runtime.constraint().getWheel(i);
            if (wheel != null && wheel.hasContact()) {
                grounded = true;
                break;
            }
        }

        return new TransportState(
                chassis.getLocation(),
                linear,
                angular,
                speedMps,
                speedKmh,
                rpm,
                gear,
                grounded,
                runtime.input(),
                runtime.trackedInput()
        );
    }

    @Override
    public @Nullable TransportHandle get(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return null;
        }
        return new TransportHandleImpl(runtime.id(), runtime);
    }

    @Override
    public @NotNull Collection<TransportHandle> getAll() {
        List<TransportHandle> handles = new ArrayList<>(byId.size());
        for (RuntimeTransport runtime : byId.values()) {
            handles.add(new TransportHandleImpl(runtime.id(), runtime));
        }
        return List.copyOf(handles);
    }

    @Override
    public @NotNull Collection<TransportHandle> getByOwner(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        String ownerName = owner.getName();
        List<TransportHandle> handles = new ArrayList<>();
        for (RuntimeTransport runtime : byId.values()) {
            if (runtime.ownerName().equals(ownerName)) {
                handles.add(new TransportHandleImpl(runtime.id(), runtime));
            }
        }
        return List.copyOf(handles);
    }

    @Override
    public @NotNull Collection<TransportHandle> getByWorld(@NotNull World world) {
        Objects.requireNonNull(world, "world");
        UUID uid = world.getUID();
        List<TransportHandle> handles = new ArrayList<>();
        for (RuntimeTransport runtime : byId.values()) {
            if (runtime.world().getWorldBukkit().getUID().equals(uid)) {
                handles.add(new TransportHandleImpl(runtime.id(), runtime));
            }
        }
        return List.copyOf(handles);
    }

    @Override
    public void setPersistentData(@NotNull Plugin owner,
                                  @NotNull TransportId id,
                                  @Nullable Map<String, String> customData) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");

        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            throw new IllegalArgumentException("Unknown transport id: " + id.value());
        }
        if (!runtime.ownerName().equals(owner.getName())) {
            throw new IllegalArgumentException("Transport is not owned by plugin: " + owner.getName());
        }

        Map<String, String> sanitized = sanitizeCustomData(customData);
        byId.computeIfPresent(id.value(), (k, existing) -> existing.withCustomData(sanitized));
    }

    @Override
    public @NotNull Map<String, String> getPersistentData(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        return runtime == null ? Map.of() : runtime.customData();
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

        List<Map<String, Object>> transports = new ArrayList<>();
        for (RuntimeTransport runtime : byId.values()) {
            if (!runtime.ownerName().equals(owner.getName())) {
                continue;
            }
            if (!runtime.chassis().isValid()) {
                continue;
            }
            Map<String, Object> serialized = serializeRuntime(runtime);
            if (!serialized.isEmpty()) {
                transports.add(serialized);
            }
        }

        yaml.set("transports", transports);
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(targetFile);
            return ApiResult.success(transports.size());
        } catch (IOException e) {
            InertiaLogger.error("Failed to save transports to " + targetFile.getAbsolutePath(), e);
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
        List<?> raw = yaml.getList("transports");
        if (raw == null || raw.isEmpty()) {
            return ApiResult.success(0);
        }

        int loaded = 0;
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            LoadedTransport loadedTransport;
            try {
                loadedTransport = deserializeTransport(map);
            } catch (Exception ex) {
                InertiaLogger.warn("Skipping invalid transport entry: " + ex.getMessage());
                continue;
            }

            World world = Bukkit.getWorld(loadedTransport.worldName());
            if (world == null) {
                continue;
            }

            ApiResult<TransportHandle> result = spawn(owner, world, loadedTransport.spec(), loadedTransport.customData());
            if (!result.isSuccess() || result.getValue() == null) {
                continue;
            }

            TransportHandle handle = result.getValue();
            setInput(handle.id(), loadedTransport.input());
            if (loadedTransport.trackedInput() != null) {
                setTrackedInput(handle.id(), loadedTransport.trackedInput());
            }
            try {
                handle.chassis().setLinearVelocity(loadedTransport.linearVelocity());
                handle.chassis().setAngularVelocity(loadedTransport.angularVelocity());
            } catch (Exception ignored) {
            }
            loaded++;
        }

        return ApiResult.success(loaded);
    }

    @Override
    public @NotNull <T> CompletableFuture<T> submitRead(@NotNull TransportId id,
                                                         @NotNull TransportNativeReadTask<T> task) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(task, "task");

        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown transport id: " + id.value()));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runtime.world().schedulePhysicsTask(() -> {
            RuntimeTransport current = byId.get(id.value());
            if (current == null) {
                future.completeExceptionally(new IllegalStateException("Transport destroyed: " + id.value()));
                return;
            }
            try {
                future.complete(task.run(new NativeReadContext(current)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public @NotNull <T> CompletableFuture<T> submitWrite(@NotNull TransportId id,
                                                          @NotNull TransportNativeWriteTask<T> task) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(task, "task");

        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown transport id: " + id.value()));
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runtime.world().schedulePhysicsTask(() -> {
            RuntimeTransport current = byId.get(id.value());
            if (current == null) {
                future.completeExceptionally(new IllegalStateException("Transport destroyed: " + id.value()));
                return;
            }
            try {
                future.complete(task.run(new NativeWriteContext(current)));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public @NotNull Collection<WheelTelemetry> getWheelTelemetry(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return List.of();
        }
        int count = runtime.constraint().countWheels();
        List<WheelTelemetry> telemetry = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            WheelTelemetry point = toWheelTelemetry(runtime.constraint(), index);
            if (point != null) {
                telemetry.add(point);
            }
        }
        return List.copyOf(telemetry);
    }

    @Override
    public @Nullable WheelTelemetry getWheelTelemetry(@NotNull TransportId id, int wheelIndex) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return null;
        }
        if (wheelIndex < 0 || wheelIndex >= runtime.constraint().countWheels()) {
            return null;
        }
        return toWheelTelemetry(runtime.constraint(), wheelIndex);
    }

    @Override
    public @NotNull Collection<WheelWorldPose> getWheelWorldPoses(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return List.of();
        }
        int count = runtime.constraint().countWheels();
        List<WheelWorldPose> poses = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            WheelWorldPose pose = toWheelWorldPose(runtime, index);
            if (pose != null) {
                poses.add(pose);
            }
        }
        return List.copyOf(poses);
    }

    @Override
    public @Nullable WheelWorldPose getWheelWorldPose(@NotNull TransportId id, int wheelIndex) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return null;
        }
        if (wheelIndex < 0 || wheelIndex >= runtime.constraint().countWheels()) {
            return null;
        }
        return toWheelWorldPose(runtime, wheelIndex);
    }

    @Override
    public @Nullable ChassisPose getChassisPose(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null || !runtime.chassis().isValid()) {
            return null;
        }
        Location location = runtime.chassis().getLocation();
        Quaternionf rotation = extractRotation(runtime.chassis(), location);
        return new ChassisPose(location, rotation);
    }

    @Override
    public boolean setWheeledDifferentialLimitedSlipRatio(@NotNull TransportId id, float ratio) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null) {
            return false;
        }
        VehicleController controller = runtime.constraint().getController();
        if (!(controller instanceof WheeledVehicleController wheeled)) {
            return false;
        }
        wheeled.setDifferentialLimitedSlipRatio(ratio);
        return true;
    }

    @Override
    public boolean overrideGravity(@NotNull TransportId id, @NotNull Vector acceleration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(acceleration, "acceleration");

        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null || !runtime.chassis().isValid()) {
            return false;
        }

        Vec3 worldGravity = runtime.world().getPhysicsSystem().getGravity();
        Vector gravityVector = new Vector(worldGravity.getX(), worldGravity.getY(), worldGravity.getZ());
        double gravityLenSq = gravityVector.lengthSquared();
        if (gravityLenSq < 1e-8d) {
            return false;
        }

        double targetLen = acceleration.length();
        if (targetLen < 1e-6d) {
            runtime.chassis().setGravityFactor(0f);
            return true;
        }

        Vector cross = gravityVector.clone().crossProduct(acceleration);
        double crossLen = cross.length();
        double maxCross = Math.sqrt(gravityLenSq) * targetLen * 1e-3d;
        if (crossLen > maxCross) {
            return false;
        }

        double factor = acceleration.dot(gravityVector) / gravityLenSq;
        runtime.chassis().setGravityFactor((float) factor);
        return true;
    }

    @Override
    public boolean resetGravityOverride(@NotNull TransportId id) {
        Objects.requireNonNull(id, "id");
        RuntimeTransport runtime = byId.get(id.value());
        if (runtime == null || !runtime.chassis().isValid()) {
            return false;
        }
        runtime.chassis().setGravityFactor(runtime.spec().chassis().bodySpec().gravityFactor());
        return true;
    }

    private static @Nullable WheelTelemetry toWheelTelemetry(@NotNull VehicleConstraint constraint, int wheelIndex) {
        Wheel wheel = constraint.getWheel(wheelIndex);
        if (wheel == null) {
            return null;
        }
        Vector contactPosition = null;
        if (wheel.hasContact()) {
            RVec3 contact = wheel.getContactPosition();
            contactPosition = new Vector(contact.xx(), contact.yy(), contact.zz());
        }
        return new WheelTelemetry(
                wheelIndex,
                wheel.hasContact(),
                wheel.getContactBodyId(),
                wheel.getSteerAngle(),
                wheel.getSuspensionLambda(),
                wheel.getLongitudinalLambda(),
                wheel.getLateralLambda(),
                wheel.getAngularVelocity(),
                wheel.getRotationAngle(),
                contactPosition
        );
    }

    private static @Nullable WheelWorldPose toWheelWorldPose(@NotNull RuntimeTransport runtime, int wheelIndex) {
        Wheel wheel = runtime.constraint().getWheel(wheelIndex);
        if (wheel == null) {
            return null;
        }

        if (!(runtime.chassis() instanceof AbstractPhysicsBody internalBody)) {
            return null;
        }

        Body body = internalBody.getBody();
        RVec3 outPosition = new RVec3();
        Quat outRotation = new Quat();

        try {
            Vec3 bodyPosition = body.getPosition().toVec3();
            Quat bodyRotation = body.getRotation();
            Vec3 bodyRotationVec = new Vec3(bodyRotation.getX(), bodyRotation.getY(), bodyRotation.getZ());
            runtime.constraint().getWheelPositionAndRotation(wheelIndex, bodyPosition, bodyRotationVec, outPosition, outRotation);
        } catch (Exception ignored) {
            Quat bodyRotation = body.getRotation();
            Vec3 local = wheel.getSettings().getPosition();
            Vector3f localOffset = new Vector3f(local.getX(), local.getY(), local.getZ());
            new Quaternionf(bodyRotation.getX(), bodyRotation.getY(), bodyRotation.getZ(), bodyRotation.getW())
                    .transform(localOffset);
            RVec3 bodyPosition = body.getPosition();
            outPosition.set(
                    bodyPosition.xx() + localOffset.x,
                    bodyPosition.yy() + localOffset.y,
                    bodyPosition.zz() + localOffset.z
            );
            outRotation.set(bodyRotation.getX(), bodyRotation.getY(), bodyRotation.getZ(), bodyRotation.getW());
        }

        return new WheelWorldPose(
                wheelIndex,
                new Vector(outPosition.xx(), outPosition.yy(), outPosition.zz()),
                new Quaternionf(outRotation.getX(), outRotation.getY(), outRotation.getZ(), outRotation.getW())
        );
    }

    private void safeDestroy(@NotNull RuntimeTransport runtime) {
        TransportHandleImpl handle = new TransportHandleImpl(runtime.id(), runtime);
        TransportLifecyclePayload payload = lifecyclePayload(
                runtime.id(),
                runtime.ownerName(),
                runtime.world().getWorldBukkit(),
                runtime.spec().type()
        );
        TransportPreDestroyEvent preDestroyEvent = new TransportPreDestroyEvent(payload, handle);
        Bukkit.getPluginManager().callEvent(preDestroyEvent);
        if (preDestroyEvent.isCancelled()) {
            byId.put(runtime.id().value(), runtime);
            return;
        }
        try {
            runtime.world().removeConstraint(runtime.constraint());
        } catch (Exception ignored) {
        }
        try {
            runtime.chassis().destroy();
        } catch (Exception ignored) {
        }
        Bukkit.getPluginManager().callEvent(new TransportPostDestroyEvent(payload));
    }

    private void applyInput(@NotNull RuntimeTransport runtime, @NotNull TransportInput input) {
        VehicleController controller = runtime.constraint().getController();
        if (controller instanceof WheeledVehicleController wheeled) {
            wheeled.setDriverInput(input.forward(), input.right(), input.brake(), input.handBrake());
            if (input.manualGear() != null) {
                wheeled.getTransmission().set(input.manualGear(), input.clutch());
            }
            return;
        }
        if (controller instanceof TrackedVehicleController tracked) {
            float leftRatio = clamp(-input.right());
            float rightRatio = clamp(input.right());
            tracked.setDriverInput(input.forward(), leftRatio, rightRatio, input.brake());
        }
    }

    private void applyTrackedInput(@NotNull RuntimeTransport runtime, @NotNull TrackedInput input) {
        VehicleController controller = runtime.constraint().getController();
        if (!(controller instanceof TrackedVehicleController tracked)) {
            return;
        }
        tracked.setDriverInput(input.forward(), input.leftRatio(), input.rightRatio(), input.brake());
    }

    private void dispatchInputApplied(@NotNull RuntimeTransport runtime,
                                      @NotNull TransportInput input,
                                      @Nullable TrackedInput trackedInput) {
        TransportInputPayload payload = new TransportInputPayload(
                TransportEventPayload.SCHEMA_VERSION_V1,
                runtime.id(),
                runtime.world().getWorldBukkit().getUID(),
                runtime.world().getWorldBukkit().getName(),
                input,
                trackedInput
        );
        Bukkit.getPluginManager().callEvent(new TransportInputAppliedEvent(payload));
    }

    private static @NotNull TransportInput trackedToCompatInput(@NotNull TrackedInput input) {
        float right = clamp((input.rightRatio() - input.leftRatio()) * 0.5f);
        return new TransportInput(input.forward(), right, input.brake(), 0f, 0f, null);
    }

    private @NotNull VehicleConstraint createVehicleConstraint(@NotNull TransportSpec spec,
                                                               @NotNull AbstractPhysicsBody chassis) {
        VehicleConstraintSettings settings = new VehicleConstraintSettings();
        settings.setController(createControllerSettings(spec));

        WheelSettings[] wheelSettings = new WheelSettings[spec.wheels().size()];
        for (int i = 0; i < spec.wheels().size(); i++) {
            wheelSettings[i] = createWheelSettings(spec.type(), spec.wheels().get(i));
        }
        settings.addWheels(wheelSettings);

        settings.setNumAntiRollBars(spec.antiRollBars().size());
        for (int i = 0; i < spec.antiRollBars().size(); i++) {
            AntiRollBarSpec antiRoll = spec.antiRollBars().get(i);
            VehicleAntiRollBar bar = settings.getAntiRollBar(i);
            bar.setLeftWheel(antiRoll.leftWheelIndex());
            bar.setRightWheel(antiRoll.rightWheelIndex());
            bar.setStiffness(antiRoll.stiffness());
        }

        VehicleConstraint constraint = new VehicleConstraint(chassis.getBody(), settings);
        constraint.setVehicleCollisionTester(createCollisionTester(spec));
        return constraint;
    }

    private @NotNull VehicleCollisionTester createCollisionTester(@NotNull TransportSpec spec) {
        CollisionTesterSpec tester = spec.collisionTester();
        Vec3 up = toVec3(tester.up());
        return switch (tester.type()) {
            case RAY -> new VehicleCollisionTesterRay(tester.objectLayer(), up, tester.maxSlopeAngleRad());
            case CAST_CYLINDER -> new VehicleCollisionTesterCastCylinder(tester.objectLayer(), 0.1f);
            case CAST_SPHERE -> new VehicleCollisionTesterCastSphere(tester.objectLayer(), estimateRadius(spec), up, tester.maxSlopeAngleRad());
        };
    }

    private static float estimateRadius(@NotNull TransportSpec spec) {
        if (spec.wheels().isEmpty()) {
            return 0.3f;
        }
        float sum = 0f;
        for (WheelSpec wheel : spec.wheels()) {
            sum += wheel.radius();
        }
        return Math.max(0.05f, sum / spec.wheels().size());
    }

    private static @NotNull VehicleControllerSettings createControllerSettings(@NotNull TransportSpec spec) {
        return switch (spec.type()) {
            case WHEELED -> createWheeledController(spec, false);
            case MOTORCYCLE -> createWheeledController(spec, true);
            case TRACKED -> new TrackedVehicleControllerSettings();
        };
    }

    private static @NotNull VehicleControllerSettings createWheeledController(@NotNull TransportSpec spec, boolean motorcycle) {
        WheeledVehicleControllerSettings settings = motorcycle
                ? new MotorcycleControllerSettings()
                : new WheeledVehicleControllerSettings();

        EngineSpec engineSpec = spec.engine();
        VehicleEngineSettings engine = settings.getEngine();
        engine.setMaxTorque(engineSpec.maxTorque());
        engine.setMinRpm(engineSpec.minRpm());
        engine.setMaxRpm(engineSpec.maxRpm());
        engine.setInertia(engineSpec.inertia());
        engine.setAngularDamping(engineSpec.angularDamping());

        TransmissionSpec transmissionSpec = spec.transmission();
        VehicleTransmissionSettings transmission = settings.getTransmission();
        transmission.setMode(transmissionSpec.mode() == TransmissionMode.AUTO ? ETransmissionMode.Auto : ETransmissionMode.Manual);
        transmission.setClutchStrength(transmissionSpec.clutchStrength());
        transmission.setSwitchLatency(transmissionSpec.switchLatency());
        transmission.setSwitchTime(transmissionSpec.switchTime());
        transmission.setShiftUpRpm(transmissionSpec.shiftUpRpm());
        transmission.setShiftDownRpm(transmissionSpec.shiftDownRpm());
        transmission.setGearRatios(toArray(transmissionSpec.forwardRatios()));
        transmission.setReverseGearRatios(toArray(transmissionSpec.reverseRatios()));

        settings.setNumDifferentials(spec.differentials().size());
        for (int i = 0; i < spec.differentials().size(); i++) {
            DifferentialSpec diffSpec = spec.differentials().get(i);
            VehicleDifferentialSettings differential = settings.getDifferential(i);
            differential.setLeftWheel(diffSpec.leftWheelIndex());
            differential.setRightWheel(diffSpec.rightWheelIndex());
            differential.setDifferentialRatio(averageWheelTorqueRatio(diffSpec));
            differential.setLimitedSlipRatio(diffSpec.limitedSlipRatio());
            differential.setEngineTorqueRatio(diffSpec.engineTorqueRatio());
        }

        return settings;
    }

    private static float averageWheelTorqueRatio(@NotNull DifferentialSpec spec) {
        float total = spec.leftWheelTorqueRatio() + spec.rightWheelTorqueRatio();
        return total <= 0f ? 1f : total / 2f;
    }

    private static float[] toArray(@NotNull List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static @NotNull WheelSettings createWheelSettings(@NotNull TransportType type,
                                                              @NotNull WheelSpec wheelSpec) {
        WheelSettings settings;
        if (type == TransportType.TRACKED) {
            settings = new WheelSettingsTv();
        } else {
            WheelSettingsWv wv = new WheelSettingsWv();
            wv.setMaxSteerAngle(wheelSpec.maxSteerAngleRad());
            wv.setMaxBrakeTorque(wheelSpec.maxBrakeTorque());
            wv.setMaxHandBrakeTorque(wheelSpec.maxHandBrakeTorque());
            settings = wv;
        }

        settings.setPosition(toVec3(wheelSpec.position()));
        settings.setSuspensionDirection(toVec3(wheelSpec.suspensionDirection()));
        settings.setRadius(wheelSpec.radius());
        settings.setWidth(wheelSpec.width());
        settings.setSuspensionMinLength(wheelSpec.suspensionMinLength());
        settings.setSuspensionMaxLength(wheelSpec.suspensionMaxLength());
        settings.getSuspensionSpring().setFrequency(wheelSpec.suspensionFrequency());
        settings.getSuspensionSpring().setDamping(wheelSpec.suspensionDamping());
        return settings;
    }

    private static Vec3 toVec3(@NotNull Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
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

    private static @NotNull TransportLifecyclePayload lifecyclePayload(@NotNull TransportId id,
                                                                       @NotNull String ownerPlugin,
                                                                       @NotNull World world,
                                                                       @NotNull TransportType transportType) {
        return new TransportLifecyclePayload(
                TransportEventPayload.SCHEMA_VERSION_V1,
                id,
                ownerPlugin,
                world.getUID(),
                world.getName(),
                transportType
        );
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        return out;
    }

    private static @NotNull Map<String, Object> serializeRuntime(@NotNull RuntimeTransport runtime) {
        PhysicsBody chassis = runtime.chassis();
        if (!chassis.isValid()) {
            return Map.of();
        }
        Location location = chassis.getLocation();
        if (location.getWorld() == null) {
            return Map.of();
        }

        Quaternionf rotation = extractRotation(chassis, location);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", runtime.id().value());
        out.put("world", location.getWorld().getName());
        out.put("spec", serializeSpec(runtime.spec(), location, rotation, chassis));
        out.put("input", serializeInput(runtime.input(), runtime.trackedInput()));
        out.put("custom-data", runtime.customData());
        out.put("velocity", Map.of(
                "linear", vec3(chassis.getLinearVelocity().getX(), chassis.getLinearVelocity().getY(), chassis.getLinearVelocity().getZ()),
                "angular", vec3(chassis.getAngularVelocity().getX(), chassis.getAngularVelocity().getY(), chassis.getAngularVelocity().getZ())
        ));
        return out;
    }

    private static @NotNull Map<String, Object> serializeSpec(@NotNull TransportSpec spec,
                                                              @NotNull Location location,
                                                              @NotNull Quaternionf rotation,
                                                              @NotNull PhysicsBody chassis) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", spec.type().name());

        Map<String, Object> chassisMap = new LinkedHashMap<>();
        chassisMap.put("body-id", chassis.getBodyId());
        chassisMap.put("motion", spec.chassis().bodySpec().motionType().name());
        chassisMap.put("mass", spec.chassis().bodySpec().mass());
        chassisMap.put("friction", chassis.getFriction());
        chassisMap.put("restitution", chassis.getRestitution());
        chassisMap.put("gravity-factor", chassis.getGravityFactor());
        chassisMap.put("linear-damping", spec.chassis().bodySpec().linearDamping());
        chassisMap.put("angular-damping", spec.chassis().bodySpec().angularDamping());
        chassisMap.put("object-layer", spec.chassis().bodySpec().objectLayer());
        chassisMap.put("location", vec3(location.getX(), location.getY(), location.getZ()));
        chassisMap.put("rotation", Map.of("x", rotation.x, "y", rotation.y, "z", rotation.z, "w", rotation.w));
        chassisMap.put("shape", serializeShape(spec.chassis().bodySpec().shape()));
        out.put("chassis", chassisMap);

        out.put("collision-tester", Map.of(
                "type", spec.collisionTester().type().name(),
                "object-layer", spec.collisionTester().objectLayer(),
                "up", vec3(spec.collisionTester().up().x, spec.collisionTester().up().y, spec.collisionTester().up().z),
                "max-slope-angle-rad", spec.collisionTester().maxSlopeAngleRad()
        ));

        out.put("engine", Map.of(
                "max-torque", spec.engine().maxTorque(),
                "min-rpm", spec.engine().minRpm(),
                "max-rpm", spec.engine().maxRpm(),
                "inertia", spec.engine().inertia(),
                "angular-damping", spec.engine().angularDamping()
        ));

        out.put("transmission", Map.of(
                "mode", spec.transmission().mode().name(),
                "clutch-strength", spec.transmission().clutchStrength(),
                "switch-latency", spec.transmission().switchLatency(),
                "switch-time", spec.transmission().switchTime(),
                "shift-up-rpm", spec.transmission().shiftUpRpm(),
                "shift-down-rpm", spec.transmission().shiftDownRpm(),
                "forward-ratios", spec.transmission().forwardRatios(),
                "reverse-ratios", spec.transmission().reverseRatios()
        ));

        List<Map<String, Object>> wheels = new ArrayList<>();
        for (WheelSpec wheel : spec.wheels()) {
            Map<String, Object> wheelMap = new LinkedHashMap<>();
            wheelMap.put("key", wheel.key());
            wheelMap.put("position", vec3(wheel.position().x, wheel.position().y, wheel.position().z));
            wheelMap.put("suspension-direction", vec3(wheel.suspensionDirection().x, wheel.suspensionDirection().y, wheel.suspensionDirection().z));
            wheelMap.put("radius", wheel.radius());
            wheelMap.put("width", wheel.width());
            wheelMap.put("suspension-min-length", wheel.suspensionMinLength());
            wheelMap.put("suspension-max-length", wheel.suspensionMaxLength());
            wheelMap.put("suspension-frequency", wheel.suspensionFrequency());
            wheelMap.put("suspension-damping", wheel.suspensionDamping());
            wheelMap.put("max-steer-angle-rad", wheel.maxSteerAngleRad());
            wheelMap.put("max-brake-torque", wheel.maxBrakeTorque());
            wheelMap.put("max-hand-brake-torque", wheel.maxHandBrakeTorque());
            wheels.add(wheelMap);
        }
        out.put("wheels", wheels);

        List<Map<String, Object>> diffs = new ArrayList<>();
        for (DifferentialSpec diff : spec.differentials()) {
            diffs.add(Map.of(
                    "left-wheel-index", diff.leftWheelIndex(),
                    "right-wheel-index", diff.rightWheelIndex(),
                    "left-wheel-torque-ratio", diff.leftWheelTorqueRatio(),
                    "right-wheel-torque-ratio", diff.rightWheelTorqueRatio(),
                    "limited-slip-ratio", diff.limitedSlipRatio(),
                    "engine-torque-ratio", diff.engineTorqueRatio()
            ));
        }
        out.put("differentials", diffs);

        List<Map<String, Object>> bars = new ArrayList<>();
        for (AntiRollBarSpec bar : spec.antiRollBars()) {
            bars.add(Map.of(
                    "left-wheel-index", bar.leftWheelIndex(),
                    "right-wheel-index", bar.rightWheelIndex(),
                    "stiffness", bar.stiffness()
            ));
        }
        out.put("anti-roll-bars", bars);

        return out;
    }

    private static @NotNull Map<String, Object> serializeInput(@NotNull TransportInput input,
                                                               @Nullable TrackedInput trackedInput) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forward", input.forward());
        out.put("right", input.right());
        out.put("brake", input.brake());
        out.put("hand-brake", input.handBrake());
        out.put("clutch", input.clutch());
        if (input.manualGear() != null) {
            out.put("manual-gear", input.manualGear());
        }
        if (trackedInput != null) {
            Map<String, Object> trackedMap = new LinkedHashMap<>();
            trackedMap.put("forward", trackedInput.forward());
            trackedMap.put("left-ratio", trackedInput.leftRatio());
            trackedMap.put("right-ratio", trackedInput.rightRatio());
            trackedMap.put("brake", trackedInput.brake());
            out.put("tracked", trackedMap);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull LoadedTransport deserializeTransport(@NotNull Map<?, ?> raw) {
        String id = String.valueOf(raw.get("id"));
        String worldName = String.valueOf(raw.get("world"));
        Map<?, ?> specMap = asMap(raw.get("spec"));

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World is not loaded: " + worldName);
        }
        TransportSpec spec = deserializeSpec(specMap, id, world);
        LoadedInput loadedInput = deserializeInput(asMap(raw.get("input")));

        Map<String, String> customData = new LinkedHashMap<>();
        Object custom = raw.get("custom-data");
        if (custom instanceof Map<?, ?> customMap) {
            for (Map.Entry<?, ?> e : customMap.entrySet()) {
                if (e.getKey() != null) {
                    customData.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
        }

        Map<?, ?> velocity = asMap(raw.get("velocity"));
        Map<?, ?> linear = asMap(velocity.get("linear"));
        Map<?, ?> angular = asMap(velocity.get("angular"));

        Vector linearVelocity = new Vector(asDouble(linear.get("x")), asDouble(linear.get("y")), asDouble(linear.get("z")));
        Vector angularVelocity = new Vector(asDouble(angular.get("x")), asDouble(angular.get("y")), asDouble(angular.get("z")));

        return new LoadedTransport(worldName, spec, loadedInput.input(), loadedInput.trackedInput(),
                Collections.unmodifiableMap(customData), linearVelocity, angularVelocity);
    }

    private static @NotNull TransportSpec deserializeSpec(@NotNull Map<?, ?> raw,
                                                          @NotNull String idValue,
                                                          @NotNull World world) {
        TransportType type = TransportType.valueOf(String.valueOf(raw.get("type")));

        Map<?, ?> chassis = asMap(raw.get("chassis"));
        Map<?, ?> loc = asMap(chassis.get("location"));
        Map<?, ?> rot = asMap(chassis.get("rotation"));
        Map<?, ?> shapeMap = asMap(chassis.get("shape"));

        Location location = new Location(world, asDouble(loc.get("x")), asDouble(loc.get("y")), asDouble(loc.get("z")));
        Quaternionf rotation = new Quaternionf(asFloat(rot.get("x")), asFloat(rot.get("y")), asFloat(rot.get("z")), asFloat(rot.get("w")));

        com.ladakx.inertia.api.physics.PhysicsBodySpec.Builder bodyBuilder = com.ladakx.inertia.api.physics.PhysicsBodySpec
                .builder(location, deserializeShape(shapeMap))
                .rotation(rotation)
                .motionType(com.ladakx.inertia.api.body.MotionType.valueOf(String.valueOf(chassis.get("motion"))))
                .mass(asFloat(chassis.get("mass")))
                .friction(asFloat(chassis.get("friction")))
                .restitution(asFloat(chassis.get("restitution")))
                .gravityFactor(asFloat(chassis.get("gravity-factor")))
                .linearDamping(asFloat(chassis.get("linear-damping")))
                .angularDamping(asFloat(chassis.get("angular-damping")))
                .bodyId((String) chassis.get("body-id"));

        if (chassis.get("object-layer") != null) {
            bodyBuilder.objectLayer(asInt(chassis.get("object-layer")));
        }

        Map<?, ?> tester = asMap(raw.get("collision-tester"));
        Map<?, ?> upMap = asMap(tester.get("up"));
        CollisionTesterSpec testerSpec = new CollisionTesterSpec(
                CollisionTesterType.valueOf(String.valueOf(tester.get("type"))),
                asInt(tester.get("object-layer")),
                new Vector3f(asFloat(upMap.get("x")), asFloat(upMap.get("y")), asFloat(upMap.get("z"))),
                asFloat(tester.get("max-slope-angle-rad"))
        );

        Map<?, ?> engine = asMap(raw.get("engine"));
        EngineSpec engineSpec = new EngineSpec(
                asFloat(engine.get("max-torque")),
                asFloat(engine.get("min-rpm")),
                asFloat(engine.get("max-rpm")),
                asFloat(engine.get("inertia")),
                asFloat(engine.get("angular-damping"))
        );

        Map<?, ?> transmission = asMap(raw.get("transmission"));
        TransmissionSpec transmissionSpec = new TransmissionSpec(
                TransmissionMode.valueOf(String.valueOf(transmission.get("mode"))),
                asFloat(transmission.get("clutch-strength")),
                asFloat(transmission.get("switch-latency")),
                asFloat(transmission.get("switch-time")),
                asFloat(transmission.get("shift-up-rpm")),
                asFloat(transmission.get("shift-down-rpm")),
                floatList(transmission.get("forward-ratios")),
                floatList(transmission.get("reverse-ratios"))
        );

        List<WheelSpec> wheels = new ArrayList<>();
        for (Object o : asList(raw.get("wheels"))) {
            Map<?, ?> wheel = asMap(o);
            Map<?, ?> pos = asMap(wheel.get("position"));
            Map<?, ?> dir = asMap(wheel.get("suspension-direction"));
            wheels.add(new WheelSpec(
                    String.valueOf(wheel.get("key")),
                    new Vector3f(asFloat(pos.get("x")), asFloat(pos.get("y")), asFloat(pos.get("z"))),
                    new Vector3f(asFloat(dir.get("x")), asFloat(dir.get("y")), asFloat(dir.get("z"))),
                    asFloat(wheel.get("radius")),
                    asFloat(wheel.get("width")),
                    asFloat(wheel.get("suspension-min-length")),
                    asFloat(wheel.get("suspension-max-length")),
                    asFloat(wheel.get("suspension-frequency")),
                    asFloat(wheel.get("suspension-damping")),
                    asFloat(wheel.get("max-steer-angle-rad")),
                    asFloat(wheel.get("max-brake-torque")),
                    asFloat(wheel.get("max-hand-brake-torque"))
            ));
        }

        List<DifferentialSpec> differentials = new ArrayList<>();
        for (Object o : asList(raw.get("differentials"))) {
            Map<?, ?> diff = asMap(o);
            differentials.add(new DifferentialSpec(
                    asInt(diff.get("left-wheel-index")),
                    asInt(diff.get("right-wheel-index")),
                    asFloat(diff.get("left-wheel-torque-ratio")),
                    asFloat(diff.get("right-wheel-torque-ratio")),
                    asFloat(diff.get("limited-slip-ratio")),
                    asFloat(diff.get("engine-torque-ratio"))
            ));
        }

        List<AntiRollBarSpec> antiRollBars = new ArrayList<>();
        for (Object o : asList(raw.get("anti-roll-bars"))) {
            Map<?, ?> bar = asMap(o);
            antiRollBars.add(new AntiRollBarSpec(
                    asInt(bar.get("left-wheel-index")),
                    asInt(bar.get("right-wheel-index")),
                    asFloat(bar.get("stiffness"))
            ));
        }

        return new TransportSpec(
                new TransportId(idValue),
                type,
                new ChassisSpec(bodyBuilder.build()),
                testerSpec,
                engineSpec,
                transmissionSpec,
                wheels,
                differentials,
                antiRollBars
        );
    }

    private static @NotNull LoadedInput deserializeInput(@NotNull Map<?, ?> raw) {
        TransportInput input = new TransportInput(
                asFloat(raw.get("forward")),
                asFloat(raw.get("right")),
                asFloat(raw.get("brake")),
                asFloat(raw.get("hand-brake")),
                asFloat(raw.get("clutch")),
                raw.get("manual-gear") == null ? null : asInt(raw.get("manual-gear"))
        );
        TrackedInput tracked = null;
        Object trackedRaw = raw.get("tracked");
        if (trackedRaw instanceof Map<?, ?> trackedMap) {
            tracked = new TrackedInput(
                    asFloat(trackedMap.get("forward")),
                    asFloat(trackedMap.get("left-ratio")),
                    asFloat(trackedMap.get("right-ratio")),
                    asFloat(trackedMap.get("brake"))
            );
        }
        return new LoadedInput(input, tracked);
    }

    private static @NotNull Map<String, Object> serializeShape(@NotNull com.ladakx.inertia.api.physics.PhysicsShape shape) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", shape.kind().name());

        if (shape instanceof com.ladakx.inertia.api.physics.BoxShape box) {
            map.put("half-x", box.halfX());
            map.put("half-y", box.halfY());
            map.put("half-z", box.halfZ());
            map.put("convex-radius", box.convexRadius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.SphereShape sphere) {
            map.put("radius", sphere.radius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.CapsuleShape capsule) {
            map.put("height", capsule.height());
            map.put("radius", capsule.radius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.CylinderShape cylinder) {
            map.put("height", cylinder.height());
            map.put("radius", cylinder.radius());
            map.put("convex-radius", cylinder.convexRadius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.TaperedCapsuleShape taperedCapsule) {
            map.put("height", taperedCapsule.height());
            map.put("top-radius", taperedCapsule.topRadius());
            map.put("bottom-radius", taperedCapsule.bottomRadius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.TaperedCylinderShape taperedCylinder) {
            map.put("height", taperedCylinder.height());
            map.put("top-radius", taperedCylinder.topRadius());
            map.put("bottom-radius", taperedCylinder.bottomRadius());
            map.put("convex-radius", taperedCylinder.convexRadius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.ConvexHullShape hull) {
            List<Map<String, Object>> points = new ArrayList<>(hull.points().size());
            for (Vector3f p : hull.points()) {
                points.add(vec3(p.x, p.y, p.z));
            }
            map.put("points", points);
            map.put("convex-radius", hull.convexRadius());
        } else if (shape instanceof com.ladakx.inertia.api.physics.CompoundShape compound) {
            List<Map<String, Object>> children = new ArrayList<>(compound.children().size());
            for (com.ladakx.inertia.api.physics.ShapeInstance child : compound.children()) {
                Map<String, Object> childMap = new LinkedHashMap<>();
                childMap.put("shape", serializeShape(child.shape()));
                childMap.put("position", vec3(child.position().x, child.position().y, child.position().z));
                childMap.put("rotation", Map.of("x", child.rotation().x, "y", child.rotation().y, "z", child.rotation().z, "w", child.rotation().w));
                if (child.centerOfMassOffset() != null) {
                    childMap.put("center-of-mass-offset", vec3(child.centerOfMassOffset().x, child.centerOfMassOffset().y, child.centerOfMassOffset().z));
                }
                children.add(childMap);
            }
            map.put("children", children);
        } else if (shape instanceof com.ladakx.inertia.api.physics.CustomShape custom) {
            map.put("type", custom.type());
            map.put("params", custom.params());
        } else {
            throw new IllegalArgumentException("Unsupported shape kind: " + shape.kind());
        }

        return map;
    }

    private static @NotNull com.ladakx.inertia.api.physics.PhysicsShape deserializeShape(@NotNull Map<?, ?> raw) {
        com.ladakx.inertia.api.physics.PhysicsShape.Kind kind = com.ladakx.inertia.api.physics.PhysicsShape.Kind.valueOf(String.valueOf(raw.get("kind")));

        return switch (kind) {
            case BOX -> new com.ladakx.inertia.api.physics.BoxShape(
                    asFloat(raw.get("half-x")),
                    asFloat(raw.get("half-y")),
                    asFloat(raw.get("half-z")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case SPHERE -> new com.ladakx.inertia.api.physics.SphereShape(asFloat(raw.get("radius")));
            case CAPSULE -> new com.ladakx.inertia.api.physics.CapsuleShape(asFloat(raw.get("height")), asFloat(raw.get("radius")));
            case CYLINDER -> new com.ladakx.inertia.api.physics.CylinderShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("radius")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case TAPERED_CAPSULE -> new com.ladakx.inertia.api.physics.TaperedCapsuleShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("top-radius")),
                    asFloat(raw.get("bottom-radius"))
            );
            case TAPERED_CYLINDER -> new com.ladakx.inertia.api.physics.TaperedCylinderShape(
                    asFloat(raw.get("height")),
                    asFloat(raw.get("top-radius")),
                    asFloat(raw.get("bottom-radius")),
                    asFloat(valueOrDefault(raw, "convex-radius", -1f))
            );
            case CONVEX_HULL -> {
                List<Vector3f> points = new ArrayList<>();
                for (Object item : asList(raw.get("points"))) {
                    Map<?, ?> p = asMap(item);
                    points.add(new Vector3f(asFloat(p.get("x")), asFloat(p.get("y")), asFloat(p.get("z"))));
                }
                yield new com.ladakx.inertia.api.physics.ConvexHullShape(points, asFloat(valueOrDefault(raw, "convex-radius", -1f)));
            }
            case COMPOUND -> {
                List<com.ladakx.inertia.api.physics.ShapeInstance> children = new ArrayList<>();
                for (Object item : asList(raw.get("children"))) {
                    Map<?, ?> child = asMap(item);
                    Map<?, ?> pos = asMap(child.get("position"));
                    Map<?, ?> rot = asMap(child.get("rotation"));
                    Map<?, ?> com = child.get("center-of-mass-offset") == null ? null : asMap(child.get("center-of-mass-offset"));
                    Vector3f comVec = com == null ? null : new Vector3f(asFloat(com.get("x")), asFloat(com.get("y")), asFloat(com.get("z")));
                    children.add(new com.ladakx.inertia.api.physics.ShapeInstance(
                            deserializeShape(asMap(child.get("shape"))),
                            new Vector3f(asFloat(pos.get("x")), asFloat(pos.get("y")), asFloat(pos.get("z"))),
                            new Quaternionf(asFloat(rot.get("x")), asFloat(rot.get("y")), asFloat(rot.get("z")), asFloat(rot.get("w"))),
                            comVec
                    ));
                }
                yield new com.ladakx.inertia.api.physics.CompoundShape(children);
            }
            case CUSTOM -> {
                Map<String, Object> params = new LinkedHashMap<>();
                Object paramsRaw = raw.get("params");
                if (paramsRaw instanceof Map<?, ?> pMap) {
                    for (Map.Entry<?, ?> e : pMap.entrySet()) {
                        if (e.getKey() != null) {
                            params.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                }
                yield new com.ladakx.inertia.api.physics.CustomShape(String.valueOf(raw.get("type")), params);
            }
        };
    }

    private static @NotNull Quaternionf extractRotation(@NotNull PhysicsBody body, @NotNull Location location) {
        if (body instanceof AbstractPhysicsBody internal) {
            Quat q = internal.getBody().getRotation();
            return new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW());
        }
        float yawRad = (float) Math.toRadians(-location.getYaw());
        float pitchRad = (float) Math.toRadians(location.getPitch());
        return new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
    }

    private static @NotNull List<Float> floatList(@Nullable Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Float> out = new ArrayList<>(list.size());
        for (Object o : list) {
            out.add(asFloat(o));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<?, ?> asMap(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected map, got: " + (raw == null ? "null" : raw.getClass().getName()));
        }
        return map;
    }

    private static @NotNull List<?> asList(@Nullable Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("Expected list, got: " + raw.getClass().getName());
    }

    private static @Nullable Object valueOrDefault(@NotNull Map<?, ?> map, @NotNull String key, @Nullable Object defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : value;
    }

    private static float asFloat(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null) {
            return 0f;
        }
        return Float.parseFloat(String.valueOf(value));
    }

    private static int asInt(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double asDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private final class TransportHandleImpl implements TransportHandle {
        private final TransportId id;
        private final RuntimeTransport snapshot;

        private TransportHandleImpl(TransportId id, @Nullable RuntimeTransport snapshot) {
            this.id = id;
            this.snapshot = snapshot;
        }

        @Override
        public @NotNull TransportId id() {
            return id;
        }

        @Override
        public @NotNull TransportOwner owner() {
            RuntimeTransport runtime = requireRuntime(id);
            return new TransportOwner(runtime.ownerName());
        }

        @Override
        public @NotNull TransportType type() {
            return requireRuntime(id).spec().type();
        }

        @Override
        public @NotNull PhysicsBody chassis() {
            return requireRuntime(id).chassis();
        }

        @Override
        public @NotNull Map<String, String> customData() {
            return requireRuntime(id).customData();
        }

        @Override
        public void close() {
            destroy(id);
        }

        private RuntimeTransport requireRuntime(TransportId id) {
            RuntimeTransport runtime = byId.get(id.value());
            if (runtime != null) {
                return runtime;
            }
            if (snapshot != null) {
                return snapshot;
            }
            throw new IllegalStateException("Transport no longer exists: " + id.value());
        }
    }

    private class NativeReadContext implements TransportNativeReadContext {
        private final RuntimeTransport runtime;

        private NativeReadContext(@NotNull RuntimeTransport runtime) {
            this.runtime = runtime;
        }

        @Override
        public @NotNull TransportId transportId() {
            return runtime.id();
        }

        @Override
        public @NotNull TransportType transportType() {
            return runtime.spec().type();
        }

        @Override
        public @NotNull com.ladakx.inertia.api.world.PhysicsWorld world() {
            return runtime.world();
        }

        @Override
        public @NotNull PhysicsBody chassis() {
            return runtime.chassis();
        }

        @Override
        public @NotNull Body chassisBody() {
            if (!(runtime.chassis() instanceof AbstractPhysicsBody internal)) {
                throw new IllegalStateException("Unsupported chassis implementation: " + runtime.chassis().getClass().getName());
            }
            return internal.getBody();
        }

        @Override
        public @NotNull VehicleConstraint constraint() {
            return runtime.constraint();
        }

        @Override
        public @NotNull VehicleController controller() {
            return runtime.constraint().getController();
        }

        @Override
        public @Nullable WheeledVehicleController wheeledController() {
            VehicleController controller = controller();
            return controller instanceof WheeledVehicleController wheeled ? wheeled : null;
        }

        @Override
        public @Nullable TrackedVehicleController trackedController() {
            VehicleController controller = controller();
            return controller instanceof TrackedVehicleController tracked ? tracked : null;
        }

        @Override
        public @Nullable MotorcycleController motorcycleController() {
            VehicleController controller = controller();
            return controller instanceof MotorcycleController motorcycle ? motorcycle : null;
        }

        @Override
        public @NotNull VehicleControllerSettings controllerSettings() {
            return controller().getSettings();
        }

        @Override
        public @Nullable WheeledVehicleControllerSettings wheeledControllerSettings() {
            VehicleControllerSettings settings = controllerSettings();
            return settings instanceof WheeledVehicleControllerSettings wheeled ? wheeled : null;
        }

        @Override
        public @Nullable TrackedVehicleControllerSettings trackedControllerSettings() {
            VehicleControllerSettings settings = controllerSettings();
            return settings instanceof TrackedVehicleControllerSettings tracked ? tracked : null;
        }

        @Override
        public @Nullable VehicleEngine engine() {
            TrackedVehicleController tracked = trackedController();
            if (tracked != null) {
                return tracked.getEngine();
            }
            WheeledVehicleController wheeled = wheeledController();
            if (wheeled != null) {
                return wheeled.getEngine();
            }
            return null;
        }

        @Override
        public @Nullable VehicleTransmission transmission() {
            WheeledVehicleController wheeled = wheeledController();
            return wheeled == null ? null : wheeled.getTransmission();
        }

        @Override
        public @NotNull Collection<Wheel> wheels() {
            int count = runtime.constraint().countWheels();
            if (count <= 0) {
                return List.of();
            }
            List<Wheel> wheels = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Wheel wheel = runtime.constraint().getWheel(i);
                if (wheel != null) {
                    wheels.add(wheel);
                }
            }
            return List.copyOf(wheels);
        }

        @Override
        public @Nullable Wheel wheel(int index) {
            if (index < 0 || index >= runtime.constraint().countWheels()) {
                return null;
            }
            return runtime.constraint().getWheel(index);
        }

        @Override
        public @NotNull Collection<VehicleAntiRollBar> antiRollBars() {
            int count = runtime.constraint().countAntiRollBars();
            if (count <= 0) {
                return List.of();
            }
            List<VehicleAntiRollBar> bars = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                VehicleAntiRollBar bar = runtime.constraint().getAntiRollBar(i);
                if (bar != null) {
                    bars.add(bar);
                }
            }
            return List.copyOf(bars);
        }

        @Override
        public @Nullable VehicleAntiRollBar antiRollBar(int index) {
            if (index < 0 || index >= runtime.constraint().countAntiRollBars()) {
                return null;
            }
            return runtime.constraint().getAntiRollBar(index);
        }

        @Override
        public @NotNull VehicleCollisionTester collisionTester() {
            VehicleCollisionTester tester = runtime.constraint().getVehicleCollisionTester();
            if (tester == null) {
                throw new IllegalStateException("Vehicle collision tester is not configured");
            }
            return tester;
        }
    }

    private final class NativeWriteContext extends NativeReadContext implements TransportNativeWriteContext {
        private NativeWriteContext(@NotNull RuntimeTransport runtime) {
            super(runtime);
        }
    }

    private record RuntimeTransport(
            TransportId id,
            String ownerName,
            com.ladakx.inertia.physics.world.PhysicsWorld world,
            TransportSpec spec,
            PhysicsBody chassis,
            VehicleConstraint constraint,
            TransportInput input,
            TrackedInput trackedInput,
            Map<String, String> customData
    ) {
        private RuntimeTransport {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ownerName, "ownerName");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(chassis, "chassis");
            Objects.requireNonNull(constraint, "constraint");
            Objects.requireNonNull(input, "input");
            customData = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(customData, "customData")));
        }

        private RuntimeTransport withInput(@NotNull TransportInput value, @Nullable TrackedInput tracked) {
            return new RuntimeTransport(id, ownerName, world, spec, chassis, constraint, value, tracked, customData);
        }

        private RuntimeTransport withCustomData(@NotNull Map<String, String> value) {
            return new RuntimeTransport(id, ownerName, world, spec, chassis, constraint, input, trackedInput, value);
        }
    }

    private record LoadedTransport(
            String worldName,
            TransportSpec spec,
            TransportInput input,
            TrackedInput trackedInput,
            Map<String, String> customData,
            Vector linearVelocity,
            Vector angularVelocity
    ) {
    }

    private record LoadedInput(
            TransportInput input,
            TrackedInput trackedInput
    ) {
    }
}
