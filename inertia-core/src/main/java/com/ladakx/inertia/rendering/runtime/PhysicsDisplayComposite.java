package com.ladakx.inertia.rendering.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PhysicsDisplayComposite {

    public record DisplayPart(
            RenderEntityDefinition definition,
            VisualEntity visual
    ) {
        public DisplayPart {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(visual);
        }
    }

    private final Body body;
    private final RenderModelDefinition model;
    private final List<DisplayPart> parts;
    private final World world;

    // Cache fields to avoid allocations during capture
    private final Vector3f cacheBodyPos = new Vector3f();
    private final Quaternionf cacheBodyRot = new Quaternionf();
    private final Vector3f cacheCenterOffset = new Vector3f();
    private final Vector3f cacheFinalPos = new Vector3f();
    private final Vector3f cacheLocalOffset = new Vector3f();
    private final Quaternionf cacheFinalRot = new Quaternionf();

    public PhysicsDisplayComposite(Body body, RenderModelDefinition model, World world, List<DisplayPart> parts) {
        this.body = Objects.requireNonNull(body);
        this.model = Objects.requireNonNull(model);
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);
    }

    public void capture(boolean sleeping, RVec3 origin, List<VisualState> accumulator, SnapshotPool pool) {
        if (parts.isEmpty()) return;

        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        // Update cached JOML objects from Jolt data
        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        // Calculate center offset (reusing static helper would be even better, but this is okay for now)
        Vector3f extent = ConvertUtils.toJOML(body.getShape().getLocalBounds().getExtent());
        cacheCenterOffset.set(extent).mul(-1f);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            VisualEntity visual = part.visual();
            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();

            // Calculate Position
            cacheFinalPos.set(cacheBodyPos);
            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                cacheBodyRot.transform(cacheLocalOffset);
                cacheFinalPos.add(cacheLocalOffset);
            }

            // Calculate Rotation
            if (model.syncRotation()) {
                cacheFinalRot.set(cacheBodyRot).mul(def.localRotation());
            } else {
                cacheFinalRot.set(def.localRotation());
            }

            // Borrow state object from pool and populate it
            VisualState state = pool.borrowState();
            state.set(
                    visual,
                    cacheFinalPos,
                    cacheFinalRot,
                    cacheCenterOffset,
                    def.rotTranslation(),
                    visible
            );
            accumulator.add(state);
        }
    }

    public void setGlowing(boolean glowing) {
        for (DisplayPart part : parts) {
            if (part.visual().isValid()) {
                part.visual().setGlowing(glowing);
            }
        }
    }

    public void markAsStatic(@org.jetbrains.annotations.Nullable java.util.UUID clusterId) {
        for (DisplayPart part : parts) {
            VisualEntity visual = part.visual();
            if (visual != null && visual.isValid()) {
                var pdc = visual.getPersistentDataContainer();
                pdc.set(
                        InertiaPDCKeys.INERTIA_ENTITY_STATIC,
                        PersistentDataType.STRING,
                        "true"
                );
                if (clusterId != null) {
                    pdc.set(
                            InertiaPDCKeys.INERTIA_CLUSTER_UUID,
                            PersistentDataType.STRING,
                            clusterId.toString()
                    );
                }
                visual.setPersistent(true);
            }
        }
    }

    public boolean isValid() {
        for (DisplayPart part : parts) {
            if (!part.visual().isValid()) {
                return false;
            }
        }
        return true;
    }

    public void destroy() {
        for (DisplayPart part : parts) {
            part.visual().remove();
        }
    }
}