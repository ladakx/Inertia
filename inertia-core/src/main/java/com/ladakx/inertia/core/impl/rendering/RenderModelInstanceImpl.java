package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.rendering.entity.RenderEntity;
import com.ladakx.inertia.api.rendering.entity.RenderModelInstance;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

final class RenderModelInstanceImpl implements RenderModelInstance {

    private final String modelId;
    private final Map<String, RenderEntityImpl> entitiesByKey;
    private boolean enabled = true;
    private boolean closed = false;

    RenderModelInstanceImpl(@NotNull String modelId, @NotNull Map<String, RenderEntityImpl> entitiesByKey) {
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.entitiesByKey = Objects.requireNonNull(entitiesByKey, "entitiesByKey");
    }

    @Override
    public @NotNull String modelId() {
        return modelId;
    }

    @Override
    public @NotNull Collection<RenderEntity> entities() {
        return java.util.Collections.unmodifiableCollection(entitiesByKey.values());
    }

    @Override
    public @Nullable RenderEntity entity(@NotNull String key) {
        Objects.requireNonNull(key, "key");
        return entitiesByKey.get(key);
    }

    @Override
    public void setBaseTransform(@NotNull Location location, @NotNull Quaternionf rotation) {
        if (closed) return;
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            entity.setBaseTransform(location, rotation);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (closed) return;
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            entity.setEnabled(enabled);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void sync() {
        if (closed) return;
        ArrayList<NetworkEntityTracker.VisualStateUpdate> updates = new ArrayList<>(entitiesByKey.size());
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            updates.add(entity.prepareStateUpdate());
        }
        if (!updates.isEmpty()) {
            RenderEntityImpl anyEntity = entitiesByKey.values().iterator().next();
            anyEntity.tracker().updateStateBatch(updates);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (RenderEntityImpl entity : entitiesByKey.values()) {
            entity.close();
        }
    }
}
