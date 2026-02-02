package com.ladakx.inertia.rendering.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.physics.world.snapshot.VisualUpdate;
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

    public PhysicsDisplayComposite(Body body, RenderModelDefinition model, World world, List<DisplayPart> parts) {
        this.body = Objects.requireNonNull(body);
        this.model = Objects.requireNonNull(model);
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);
    }

    // Updated to accept Origin
    public List<VisualUpdate> capture(boolean sleeping, RVec3 origin) {
        if (parts.isEmpty()) return Collections.emptyList();

        List<VisualUpdate> updates = new ArrayList<>(parts.size());

        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        // Apply Origin Offset to get World Position
        Vector3f bodyPos = new Vector3f(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );

        Quaternionf bodyRot = new Quaternionf(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());
        Vector3f centerOffset = ConvertUtils.toJOML(body.getShape().getLocalBounds().getExtent()).mul(-1f);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            VisualEntity visual = part.visual();

            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            Vector3f finalPos = new Vector3f(bodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                Vector3f localOffset = new Vector3f((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                bodyRot.transform(localOffset);
                finalPos.add(localOffset);
            }

            Quaternionf finalRot = new Quaternionf();
            if (model.syncRotation()) {
                finalRot.set(bodyRot).mul(def.localRotation());
            } else {
                finalRot.set(def.localRotation());
            }

            updates.add(new VisualUpdate(
                    visual,
                    finalPos,
                    finalRot,
                    centerOffset,
                    def.rotTranslation(),
                    visible
            ));
        }
        return updates;
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