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

/**
 * Manages the visual representation of a physics body.
 * Now separated into capture (math/physics thread) and apply (main thread) phases.
 */
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

    /**
     * Captures the current state of visuals based on physics body data.
     * Executed on the PHYSICS THREAD.
     *
     * @param sleeping Whether the body is currently sleeping.
     * @return A list of update instructions for the main thread.
     */
    public List<VisualUpdate> capture(boolean sleeping) {
        if (parts.isEmpty()) return Collections.emptyList();

        List<VisualUpdate> updates = new ArrayList<>(parts.size());

        // 1. Get raw physics data
        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        // 2. Convert to JOML for math operations (High performance)
        // Note: keeping precision as float for rendering relative to world is standard for Minecraft entities
        Vector3f bodyPos = new Vector3f((float) bodyPosJolt.xx(), (float) bodyPosJolt.yy(), (float) bodyPosJolt.zz());
        Quaternionf bodyRot = new Quaternionf(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        // 3. Pre-calculate center offset for block displays (Jolt CoM vs Minecraft Origin)
        // ConvertUtils.toJOML returns Vector3f
        Vector3f centerOffset = ConvertUtils.toJOML(body.getShape().getLocalBounds().getExtent()).mul(-1f);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            VisualEntity visual = part.visual();

            // --- Visibility Logic ---
            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();

            // If invisible and wasn't visible, we could optimize, but we need to send the packet at least once.
            // For now, we calculate positions even if invisible to ensure state consistency when it reappears.

            // --- Position Calculation ---
            Vector3f finalPos = new Vector3f(bodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                Vector3f localOffset = new Vector3f((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());

                // Rotate the local offset by the body's rotation
                // P_world = P_body + (R_body * P_local_offset)
                bodyRot.transform(localOffset);
                finalPos.add(localOffset);
            }

            // --- Rotation Calculation ---
            Quaternionf finalRot = new Quaternionf();
            if (model.syncRotation()) {
                // R_final = R_body * R_local
                finalRot.set(bodyRot).mul(def.localRotation());
            } else {
                finalRot.set(def.localRotation());
            }

            // Create immutable update instruction
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
        // This is still called from Main Thread via Tools, so we delegate directly
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

                // Маркируем как статику
                pdc.set(
                        InertiaPDCKeys.INERTIA_ENTITY_STATIC,
                        PersistentDataType.STRING,
                        "true"
                );

                // Если передан ID кластера (для цепей/рэгдоллов), записываем его
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

    public void destroy() {
        // Called from Main Thread
        for (DisplayPart part : parts) {
            part.visual().remove();
        }
    }
}