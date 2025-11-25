package com.ladakx.inertia.render.runtime;

import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Композит, який тримає всі візуальні сутності одного фіз. об'єкта
 * і вміє їх оновлювати згідно з Jolt-Body.
 */
public final class PhysicsDisplayComposite {

    public record DisplayPart(
            RenderEntityDefinition definition,
            Entity entity
    ) {
        public DisplayPart {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(entity, "entity");
        }
    }

    private final Body body;
    private final RenderModelDefinition model;
    private final List<DisplayPart> parts;
    private volatile boolean sleeping;

    public PhysicsDisplayComposite(Body body,
                                   RenderModelDefinition model,
                                   List<DisplayPart> parts) {
        this.body = Objects.requireNonNull(body, "body");
        this.model = Objects.requireNonNull(model, "model");
        this.parts = Collections.unmodifiableList(parts);
        this.sleeping = false;
    }

    public List<DisplayPart> parts() {
        return parts;
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
        refreshVisibility();
    }

    private void refreshVisibility() {
        for (DisplayPart part : parts) {
            Entity entity = part.entity();
            if (!entity.isValid()) {
                continue;
            }
            RenderEntityDefinition def = part.definition();
            boolean visible = sleeping
                    ? def.showWhenSleeping()
                    : def.showWhenActive();

            entity.setInvisible(!visible);
        }
    }

    /**
     * Оновити позиції/обертання всіх локальних сутностей згідно з тілом.
     * Викликати з тік-лупу плагіна (в main-потоці Bukkit).
     */
    public void update() {
        if (parts.isEmpty()) {
            return;
        }

        RVec3 pos = body.getPosition();
        Quat rot = body.getRotation();

        Quaternionf bodyRot = new Quaternionf(
                rot.getX(),
                rot.getY(),
                rot.getZ(),
                rot.getW()
        );

        for (DisplayPart part : parts) {
            Entity entity = part.entity();
            if (!entity.isValid()) {
                continue;
            }

            RenderEntityDefinition def = part.definition();

            Location current = entity.getLocation();
            Location target = current.clone();
            target.setWorld(current.getWorld());

            if (model.syncPosition()) {
                Vector offset = def.localOffset().clone();
                // Q_body * offset_local
                org.joml.Vector3f off = new org.joml.Vector3f(
                        (float) offset.getX(),
                        (float) offset.getY(),
                        (float) offset.getZ()
                );
                bodyRot.transform(off);

                double x = pos.xx() + off.x;
                double y = pos.yy() + off.y;
                double z = pos.zz() + off.z;

                target.setX(x);
                target.setY(y);
                target.setZ(z);
            }

            if (model.syncRotation()) {
                Quaternionf worldRot = new Quaternionf(bodyRot).mul(def.localRotation());

                if (entity instanceof Display display) {
                    Transformation trans = display.getTransformation();
                    Transformation newTrans = new Transformation(trans.getTranslation(), worldRot, trans.getScale(), trans.getRightRotation());
                    display.setTransformation(newTrans);
                }

                if (entity instanceof ArmorStand stand) {
                    EulerAngle headPose = RotationUtils.toEulerAngle(worldRot);
                    stand.setHeadPose(headPose);
                }
            }

            entity.teleport(target);
        }

        refreshVisibility();
    }

    public void destroy() {
        for (DisplayPart part : parts) {
            Entity entity = part.entity();
            if (entity.isValid()) {
                entity.remove();
            }
        }
    }
}