package com.ladakx.inertia.render.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PhysicsDisplayComposite {

    public record DisplayPart(
            RenderEntityDefinition definition,
            VisualObject visual
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
    private volatile boolean sleeping;

    public PhysicsDisplayComposite(Body body, RenderModelDefinition model, World world, List<DisplayPart> parts) {
        this.body = Objects.requireNonNull(body);
        this.model = Objects.requireNonNull(model);
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);
        this.sleeping = false;
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
        refreshVisibility();
    }

    private void refreshVisibility() {
        for (DisplayPart part : parts) {
            if (!part.visual().isValid()) continue;

            RenderEntityDefinition def = part.definition();
            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            part.visual().setVisible(visible);
        }
    }

    public void update() {
        if (parts.isEmpty()) return;

        RVec3 pos = body.getPosition();
        Quat rot = body.getRotation();

        // Основна ротація тіла
        Quaternionf bodyRot = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());

        // Базова локація (Jolt coordinate -> Bukkit coordinate)
        // Примітка: Обережно з потоками, Location створення має бути sync або безпечним
        Location baseLoc = new Location(world, pos.xx(), pos.yy(), pos.zz());

        for (DisplayPart part : parts) {
            VisualObject visual = part.visual();
            if (!visual.isValid()) continue;

            RenderEntityDefinition def = part.definition();
            Location target = baseLoc.clone();

            // 1. Position Sync
            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                Vector3f off = new Vector3f(
                        (float) offset.getX(), (float) offset.getY(), (float) offset.getZ()
                );
                bodyRot.transform(off); // Rotate offset by body rotation
                target.add(off.x, off.y, off.z);
            }

            // 2. Rotation & Scale Sync
            Quaternionf finalRot = new Quaternionf();
            if (model.syncRotation()) {
                finalRot.set(bodyRot).mul(def.localRotation());
            } else {
                finalRot.set(def.localRotation());
            }

            Vector s = def.scale();
            Vector3f finalScale = new Vector3f((float)s.getX(), (float)s.getY(), (float)s.getZ());

            // Делегуємо оновлення реалізації (ArmorStand або DisplayEntity знають як це обробити)
            visual.update(target, finalRot, finalScale);
        }

        refreshVisibility();
    }

    public void destroy() {
        for (DisplayPart part : parts) {
            part.visual().remove();
        }
    }
}