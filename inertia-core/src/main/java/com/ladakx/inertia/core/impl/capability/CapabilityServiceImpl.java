package com.ladakx.inertia.core.impl.capability;

import com.ladakx.inertia.api.capability.ApiCapability;
import com.ladakx.inertia.api.capability.CapabilityService;
import com.ladakx.inertia.api.version.ApiVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class CapabilityServiceImpl implements CapabilityService {

    private final ApiVersion apiVersion;
    private final Set<ApiCapability> capabilities;

    public CapabilityServiceImpl(@NotNull ApiVersion apiVersion, @NotNull Set<ApiCapability> capabilities) {
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(capabilities, "capabilities");
        this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    @Override
    public boolean supports(@NotNull ApiCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    @Override
    public @NotNull ApiVersion apiVersion() {
        return apiVersion;
    }
}
