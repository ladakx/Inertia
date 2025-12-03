package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.physics.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.registry.PhysicsModelRegistry;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import com.ladakx.inertia.utils.jolt.ConvertUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockPhysicsObject extends DisplayedPhysicsObject implements InertiaPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;
    private boolean removed = false;

    public BlockPhysicsObject(@NotNull MinecraftSpace space,
                              @NotNull String bodyId,
                              @NotNull PhysicsModelRegistry modelRegistry,
                              @NotNull RenderFactory renderFactory,
                              @NotNull RVec3 initialPosition,
                              @NotNull Quat initialRotation) {
        // Створення налаштувань тіла з урахуванням типу дефініції
        super(space, createBodySettings(bodyId, modelRegistry, initialPosition, initialRotation));
        this.bodyId = bodyId;

        PhysicsModelRegistry.BodyModel model = modelRegistry.require(bodyId);

        // Отримуємо візуальну модель, якщо вона була успішно зарезолвлена в реєстрі
        Optional<RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isPresent()) {
            RenderModelDefinition renderDef = renderOpt.get();
            World world = space.getWorldBukkit();

            // Створюємо початкову локацію для спавну візуалів
            Location spawnLocation = new Location(world, initialPosition.xx(), initialPosition.yy(), initialPosition.zz());

            // Збираємо частини композиту
            List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
            for (RenderEntityDefinition entityDef : renderDef.entities().values()) {
                VisualObject visual = renderFactory.create(world, spawnLocation, entityDef);
                if (visual.isValid()) {
                    parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
                }
            }

            this.displayComposite = new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
            this.displayComposite.update();
        } else {
            this.displayComposite = null;
        }
    }

    // --- Static Helpers ---

    private static BodyCreationSettings createBodySettings(String bodyId,
                                                           PhysicsModelRegistry modelRegistry,
                                                           RVec3 initialPosition,
                                                           Quat initialRotation) {
        PhysicsModelRegistry.BodyModel model = modelRegistry.require(bodyId);
        BlockBodyDefinition def = (BlockBodyDefinition) model.bodyDefinition();

        BodyPhysicsSettings phys;
        List<String> shapeLines;

        // Pattern Matching для витягування параметрів залежно від типу тіла
        phys = def.physicsSettings();
        shapeLines = def.shapeLines();

        ConstShape shape = JShapeFactory.createShape(shapeLines);

        InertiaLogger.debug("Creating body '" + bodyId + "' type=" + def.getClass().getSimpleName());
        InertiaLogger.debug("Body physics: " +
                "mass=" + phys.mass() +
                ", motionType=" + phys.motionType() +
                ", objectLayer=" + phys.objectLayer()
        );

        BodyCreationSettings settings = new BodyCreationSettings()
                .setShape(shape)
                .setMotionType(phys.motionType())
                .setObjectLayer(phys.objectLayer())
                .setLinearDamping(phys.linearDamping())
                .setAngularDamping(phys.angularDamping());

        if (phys.motionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
            settings.setMotionQuality(EMotionQuality.LinearCast);
        }

        settings.getMassProperties().setMass(phys.mass());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());

        settings.setPosition(initialPosition);
        settings.setRotation(initialRotation);

        return settings;
    }

    // --- AbstractPhysicsObject Implementation ---

    @Override
    public @Nullable PhysicsDisplayComposite getDisplay() {
        return displayComposite;
    }

    @Override
    public void update() {
        if (removed) return;
        if (displayComposite != null) {
            displayComposite.update();
        }
    }

    public void destroy() {
        if (removed) return;
        removed = true;

        // Видалення з простору (Jolt)
        if (getBody() != null) { // Null-check на випадок помилки ініціалізації
            getSpace().removeObject(this);
            getSpace().getBodyInterface().removeBody(getBody().getId());
            getSpace().getBodyInterface().destroyBody(getBody().getId());
        }

        // Видалення візуалу
        if (displayComposite != null) {
            displayComposite.destroy();
        }
    }

    // --- InertiaPhysicsObject Implementation (API) ---

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsObjectType getType() {
        return PhysicsObjectType.BLOCK;
    }

    @Override
    public void remove() {
        destroy();
    }

    @Override
    public boolean isValid() {
        return !removed && getBody() != null && !getBody().isActive(); // Перевірка active може бути інвертована залежно від логіки
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (!isValid()) return;
        RVec3 pos = new RVec3(location.getX(), location.getY(), location.getZ());
        getSpace().getBodyInterface().setPosition(getBody().getId(), pos, com.github.stephengold.joltjni.enumerate.EActivation.Activate);
    }

    @Override
    public void setLinearVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        Vec3 joltVel = ConvertUtils.toVec3(velocity);
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), joltVel);
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0);
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}