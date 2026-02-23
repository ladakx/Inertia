package com.ladakx.inertia.api.capability;

import com.ladakx.inertia.api.version.ApiVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface CapabilityService {

    boolean supports(@NotNull ApiCapability capability);

    @NotNull ApiVersion apiVersion();

    default void require(@NotNull ApiCapability capability) {
        Objects.requireNonNull(capability, "capability");
        if (!supports(capability)) {
            throw new IllegalStateException("Unsupported API capability: " + capability.name());
        }
    }
}
