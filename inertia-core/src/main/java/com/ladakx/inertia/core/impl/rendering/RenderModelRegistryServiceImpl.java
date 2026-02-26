package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.ApiErrorCode;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.rendering.model.RenderIdPolicy;
import com.ladakx.inertia.api.rendering.model.RenderModelRegistryService;
import com.ladakx.inertia.configuration.dto.RenderConfig;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RenderModelRegistryServiceImpl implements RenderModelRegistryService {

    private record Entry(Plugin owner, RenderModelDefinition model) {}

    private final ConcurrentMap<String, Entry> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Plugin, Set<String>> idsByOwner = new ConcurrentHashMap<>();

    @Override
    public @NotNull ApiResult<Void> registerModel(@NotNull Plugin owner, @NotNull RenderModelDefinition model) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(model, "model");
        String id = Objects.requireNonNull(model.id(), "model.id()");
        if (id.isBlank()) {
            return ApiResult.failure(ApiErrorCode.INVALID_SPEC, "render.model-id-blank");
        }

        Entry prev = byId.putIfAbsent(id, new Entry(owner, model));
        if (prev != null) {
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "render.model-already-registered");
        }
        idsByOwner.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(id);
        return ApiResult.success(null);
    }

    @Override
    public @NotNull ApiResult<Integer> registerFromConfigSection(@NotNull Plugin owner,
                                                                 @NotNull ConfigurationSection section,
                                                                 @NotNull RenderIdPolicy idPolicy) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(idPolicy, "idPolicy");

        RenderConfig parsed = new RenderConfig(section);

        int ok = 0;
        for (String rawId : section.getKeys(false)) {
            if (rawId == null || rawId.isBlank()) continue;

            String id = mapId(owner, rawId, idPolicy);
            RenderModelDefinition selected = parsed.find(rawId).orElse(null);
            if (selected == null) {
                continue;
            }

            RenderModelDefinition def = selected;
            if (!Objects.equals(selected.id(), id)) {
                def = new RenderModelDefinition(id, selected.syncPosition(), selected.syncRotation(), selected.entities());
            }

            ApiResult<Void> res = registerModel(owner, def);
            if (res.isSuccess()) {
                ok++;
            }
        }

        return ApiResult.success(ok);
    }

    private static @NotNull String mapId(@NotNull Plugin owner, @NotNull String rawId, @NotNull RenderIdPolicy policy) {
        return switch (policy) {
            case AS_IS -> rawId;
            case NAMESPACE_OWNER_IF_MISSING -> rawId.contains(":") ? rawId : (owner.getName() + ":" + rawId);
        };
    }

    @Override
    public @Nullable RenderModelDefinition get(@NotNull String id) {
        Objects.requireNonNull(id, "id");
        Entry e = byId.get(id);
        return e == null ? null : e.model;
    }

    @Override
    public @NotNull Collection<RenderModelDefinition> getAll() {
        ArrayList<RenderModelDefinition> out = new ArrayList<>(byId.size());
        for (Entry e : byId.values()) {
            out.add(e.model);
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public @NotNull ApiResult<Void> unregister(@NotNull String id) {
        Objects.requireNonNull(id, "id");
        Entry removed = byId.remove(id);
        if (removed == null) {
            return ApiResult.failure(ApiErrorCode.UNSUPPORTED_OPERATION, "render.model-not-found");
        }
        idsByOwner.computeIfPresent(removed.owner, (owner, set) -> {
            set.remove(id);
            return set.isEmpty() ? null : set;
        });
        return ApiResult.success(null);
    }

    @Override
    public void unregisterAll(@NotNull Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        Set<String> ids = idsByOwner.remove(owner);
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            byId.remove(id);
        }
    }
}
