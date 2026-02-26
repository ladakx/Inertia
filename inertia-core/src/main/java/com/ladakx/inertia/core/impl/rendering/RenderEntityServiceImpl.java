package com.ladakx.inertia.core.impl.rendering;

import com.ladakx.inertia.api.rendering.entity.RenderEntity;
import com.ladakx.inertia.api.rendering.entity.RenderEntityService;
import com.ladakx.inertia.api.rendering.entity.RenderModelInstance;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.tracker.NetworkEntityTracker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RenderEntityServiceImpl implements RenderEntityService {

    private final RenderFactory renderFactory;
    private final NetworkEntityTracker tracker;

    public RenderEntityServiceImpl(@NotNull RenderFactory renderFactory, @NotNull NetworkEntityTracker tracker) {
        this.renderFactory = Objects.requireNonNull(renderFactory, "renderFactory");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @Override
    public @NotNull RenderEntity createEntity(@NotNull World world,
                                              @NotNull Location location,
                                              @NotNull Quaternionf rotation,
                                              @NotNull RenderEntityDefinition definition) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(definition, "definition");

        NetworkVisual visual = renderFactory.create(world, location, definition);
        RenderEntityImpl entity = new RenderEntityImpl(
                tracker,
                visual,
                definition.key(),
                definition.localOffset(),
                definition.localRotation(),
                true,
                true,
                null
        );
        entity.setBaseTransform(location, rotation);
        tracker.register(visual, entity.trackerLocation(), entity.trackerRotation(), null, visual.getId(), -1, false, 0x07, true);
        return entity;
    }

    @Override
    public @NotNull RenderEntity registerVisual(@NotNull Location location,
                                                @NotNull Quaternionf rotation,
                                                @NotNull NetworkVisual visual) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(visual, "visual");

        RenderEntityImpl entity = new RenderEntityImpl(
                tracker,
                visual,
                "custom:" + visual.getId(),
                new Vector(0, 0, 0),
                new Quaternionf(),
                true,
                true,
                null
        );
        entity.setBaseTransform(location, rotation);
        tracker.register(visual, entity.trackerLocation(), entity.trackerRotation(), null, visual.getId(), -1, false, 0x07, true);
        return entity;
    }

    @Override
    public @NotNull RenderModelInstance createModel(@NotNull World world,
                                                    @NotNull Location location,
                                                    @NotNull Quaternionf rotation,
                                                    @NotNull RenderModelDefinition modelDefinition) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(modelDefinition, "modelDefinition");

        Map<String, RenderEntityImpl> byKey = new LinkedHashMap<>();
        List<NetworkEntityTracker.VisualRegistration> regs = new ArrayList<>(modelDefinition.entities().size());
        int groupKey = -1;
        int firstVisualId = -1;

        for (Map.Entry<String, RenderEntityDefinition> entry : modelDefinition.entities().entrySet()) {
            String key = entry.getKey();
            RenderEntityDefinition def = entry.getValue();
            if (def == null) continue;

            NetworkVisual visual = renderFactory.create(world, location, def);
            if (firstVisualId < 0) {
                firstVisualId = visual.getId();
            }
            if (groupKey < 0 && def.placeOn() == null) {
                groupKey = visual.getId();
            }
            RenderEntityImpl entity = new RenderEntityImpl(
                    tracker,
                    visual,
                    key,
                    def.localOffset(),
                    def.localRotation(),
                    modelDefinition.syncPosition() && def.placeOn() == null,
                    modelDefinition.syncRotation(),
                    def.placeOn()
            );
            byKey.put(key, entity);
        }

        if (groupKey < 0) {
            groupKey = firstVisualId;
        }

        RenderModelInstanceImpl model = new RenderModelInstanceImpl(modelDefinition.id(), byKey);
        model.setBaseTransform(location, rotation);
        model.recomputeForSpawn();

        for (RenderEntityImpl entity : model.orderedEntitiesForSpawn()) {
            NetworkVisual visual = entity.visual();
            int mountVehicleId = -1;
            String parentKey = entity.placeOnKey();
            if (parentKey != null) {
                RenderEntityImpl parent = byKey.get(parentKey);
                if (parent != null) {
                    mountVehicleId = parent.visual().getId();
                }
            }
            regs.add(new NetworkEntityTracker.VisualRegistration(
                    visual,
                    entity.trackerLocation(),
                    entity.trackerRotation(),
                    null,
                    groupKey,
                    mountVehicleId,
                    false,
                    0x07,
                    true
            ));
        }

        tracker.registerBatch(regs);
        return model;
    }

}
