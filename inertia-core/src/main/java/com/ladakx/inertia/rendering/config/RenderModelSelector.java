package com.ladakx.inertia.rendering.config;

import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.version.ClientVersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface RenderModelSelector {

    @NotNull List<RenderModelVariant> variants();

    default @Nullable RenderModelVariant selectByProtocol(int clientProtocol) {
        RenderModelVariant fallback = null;
        for (RenderModelVariant v : variants()) {
            if (v == null) continue;
            ClientVersionRange range = v.clientRange();
            if (range == null) {
                fallback = v;
                continue;
            }
            if (range.containsProtocol(clientProtocol)) {
                return v;
            }
        }
        return fallback;
    }

    default @Nullable RenderModelDefinition selectModelByProtocol(int clientProtocol) {
        RenderModelVariant v = selectByProtocol(clientProtocol);
        return v != null ? v.model() : null;
    }
}

