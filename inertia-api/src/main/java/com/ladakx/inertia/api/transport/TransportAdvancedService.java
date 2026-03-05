package com.ladakx.inertia.api.transport;

import com.ladakx.inertia.api.ExecutionContext;
import com.ladakx.inertia.api.ThreadingPolicy;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ExecutionContext(ThreadingPolicy.ANY_THREAD)
public interface TransportAdvancedService {

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<WheelTelemetry> getWheelTelemetry(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable WheelTelemetry getWheelTelemetry(@NotNull TransportId id, int wheelIndex);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @NotNull Collection<WheelWorldPose> getWheelWorldPoses(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable WheelWorldPose getWheelWorldPose(@NotNull TransportId id, int wheelIndex);

    @ExecutionContext(ThreadingPolicy.ANY_THREAD)
    @Nullable ChassisPose getChassisPose(@NotNull TransportId id);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean setWheeledDifferentialLimitedSlipRatio(@NotNull TransportId id, float ratio);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean overrideGravity(@NotNull TransportId id, @NotNull Vector acceleration);

    @ExecutionContext(ThreadingPolicy.MAIN_THREAD_ONLY)
    boolean resetGravityOverride(@NotNull TransportId id);
}
