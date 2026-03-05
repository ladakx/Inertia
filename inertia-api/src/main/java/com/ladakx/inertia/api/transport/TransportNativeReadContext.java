package com.ladakx.inertia.api.transport;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.MotorcycleController;
import com.github.stephengold.joltjni.TrackedVehicleController;
import com.github.stephengold.joltjni.TrackedVehicleControllerSettings;
import com.github.stephengold.joltjni.VehicleAntiRollBar;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleConstraint;
import com.github.stephengold.joltjni.VehicleController;
import com.github.stephengold.joltjni.VehicleControllerSettings;
import com.github.stephengold.joltjni.VehicleEngine;
import com.github.stephengold.joltjni.VehicleTransmission;
import com.github.stephengold.joltjni.Wheel;
import com.github.stephengold.joltjni.WheeledVehicleController;
import com.github.stephengold.joltjni.WheeledVehicleControllerSettings;
import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.world.PhysicsWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Read-only native context for a concrete transport.
 * <p>
 * All returned native objects are task-scoped and must not be retained.
 */
@ExecutionContext(ThreadingPolicy.PHYSICS_THREAD_READONLY)
public interface TransportNativeReadContext {

    @NotNull TransportId transportId();

    @NotNull TransportType transportType();

    @NotNull PhysicsWorld world();

    @NotNull PhysicsBody chassis();

    @NotNull Body chassisBody();

    @NotNull VehicleConstraint constraint();

    @NotNull VehicleController controller();

    @Nullable WheeledVehicleController wheeledController();

    @Nullable TrackedVehicleController trackedController();

    @Nullable MotorcycleController motorcycleController();

    @NotNull VehicleControllerSettings controllerSettings();

    @Nullable WheeledVehicleControllerSettings wheeledControllerSettings();

    @Nullable TrackedVehicleControllerSettings trackedControllerSettings();

    @Nullable VehicleEngine engine();

    @Nullable VehicleTransmission transmission();

    @NotNull Collection<Wheel> wheels();

    @Nullable Wheel wheel(int index);

    @NotNull Collection<VehicleAntiRollBar> antiRollBars();

    @Nullable VehicleAntiRollBar antiRollBar(int index);

    @NotNull VehicleCollisionTester collisionTester();
}
