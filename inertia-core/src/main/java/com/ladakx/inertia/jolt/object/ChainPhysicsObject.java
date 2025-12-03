package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.config.ChainBodyDefinition;
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

/**
 * Фізичний об'єкт ланцюга.
 * Наслідує AbstractPhysicsObject, керує власним візуальним відображенням
 * та створює Constraint з батьківським тілом.
 */
public class ChainPhysicsObject extends DisplayedPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;
    private boolean removed = false;

    public ChainPhysicsObject(@NotNull MinecraftSpace space,
                              @NotNull String bodyId,
                              @NotNull PhysicsModelRegistry modelRegistry,
                              @NotNull RenderFactory renderFactory,
                              @NotNull RVec3 initialPosition,
                              @NotNull Quat initialRotation,
                              @Nullable Body parentBody) {
        // 1. Ініціалізація фізичного тіла через батьківський конструктор
        super(space, createBodySettings(bodyId, modelRegistry, initialPosition, initialRotation));
        this.bodyId = bodyId;

        // 2. Створення Constraint (З'єднання), якщо є батьківське тіло
        if (parentBody != null) {
            createConstraint(modelRegistry, bodyId, parentBody);
        }

        // 3. Ініціалізація візуальної частини (Display)
        this.displayComposite = createVisuals(space, bodyId, modelRegistry, renderFactory, initialPosition);
        update(); // Перше оновлення позиції візуалів
    }

    private void createConstraint(PhysicsModelRegistry registry, String bodyId, Body parentBody) {
        PhysicsModelRegistry.BodyModel model = registry.require(bodyId);
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition chainDef)) {
            return;
        }

        // PointConstraint фіксує дві точки тіл разом (шарнір)
        // Розраховуємо точку прив'язки. Вона знаходиться між поточною ланкою та батьківською.
        // Використовуємо joint-offset з конфігу (bodies.yml)
        double jointOffset = chainDef.chainSettings().jointOffset();

        RVec3 currentPos = getBody().getPosition();
        // Точка з'єднання трохи вище центру поточної ланки (в World Space)
        RVec3 pivotPoint = new RVec3(
                currentPos.xx(),
                currentPos.yy() + jointOffset,
                currentPos.zz()
        );

        PointConstraintSettings settings = new PointConstraintSettings();
        settings.setSpace(EConstraintSpace.WorldSpace);
        settings.setPoint1(pivotPoint);
        settings.setPoint2(pivotPoint);

        TwoBodyConstraint constraint = settings.create(parentBody, getBody());

        // Додаємо constraint у простір та реєструємо його в абстрактному класі для менеджменту
        getSpace().addConstraint(constraint);
        addRelatedConstraint(constraint.toRef());
    }

    private PhysicsDisplayComposite createVisuals(MinecraftSpace space, String bodyId,
                                                  PhysicsModelRegistry registry, RenderFactory factory,
                                                  RVec3 initialPos) {
        PhysicsModelRegistry.BodyModel model = registry.require(bodyId);
        Optional<RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isEmpty()) return null;

        RenderModelDefinition renderDef = renderOpt.get();
        World world = space.getWorldBukkit();
        Location spawnLoc = new Location(world, initialPos.xx(), initialPos.yy(), initialPos.zz());

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
        for (RenderEntityDefinition entityDef : renderDef.entities().values()) {
            VisualObject visual = factory.create(world, spawnLoc, entityDef);
            if (visual.isValid()) {
                parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
            }
        }
        return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
    }

    private static BodyCreationSettings createBodySettings(String bodyId, PhysicsModelRegistry registry,
                                                           RVec3 pos, Quat rot) {
        PhysicsModelRegistry.BodyModel model = registry.require(bodyId);

        // Перевіряємо, чи це дійсно ланцюг
        if (!(model.bodyDefinition() instanceof ChainBodyDefinition def)) {
            throw new IllegalArgumentException("Body '" + bodyId + "' is not a CHAIN definition.");
        }

        BodyPhysicsSettings phys = def.physicsSettings();
        ConstShape shape = JShapeFactory.createShape(def.shapeLines());

        BodyCreationSettings settings = new BodyCreationSettings();
        settings.setShape(shape);
        settings.setPosition(pos);
        settings.setRotation(rot);
        settings.setMotionType(phys.motionType());
        settings.setObjectLayer(phys.objectLayer());
        settings.getMassProperties().setMass(phys.mass());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());
        settings.setLinearDamping(phys.linearDamping());
        settings.setAngularDamping(phys.angularDamping());

        // Для ланцюгів важливо використовувати LinearCast для кращої симуляції з'єднань
        if (phys.motionType() == com.github.stephengold.joltjni.enumerate.EMotionType.Dynamic) {
            settings.setMotionQuality(EMotionQuality.LinearCast);
        }

        return settings;
    }

    // --- AbstractPhysicsObject Implementation ---

    @Override
    public @Nullable PhysicsDisplayComposite getDisplay() {
        return displayComposite;
    }

    @Override
    public void update() {
        if (removed || displayComposite == null) return;
        displayComposite.update();
    }

    // --- InertiaPhysicsObject Implementation ---

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsObjectType getType() {
        return PhysicsObjectType.CHAIN;
    }

    @Override
    public void remove() {
        destroy();
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;

        super.destroy();

        // Видалення візуалу
        if (displayComposite != null) {
            displayComposite.destroy();
        }
    }

    @Override
    public boolean isValid() {
        return !removed && getBody() != null && !getBody().isActive();
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (!isValid()) return;
        RVec3 pos = new RVec3(location.getX(), location.getY(), location.getZ());
        getSpace().getBodyInterface().setPosition(getBody().getId(), pos, EActivation.Activate);
    }

    @Override
    public void setLinearVelocity(@NotNull Vector velocity) {
        if (!isValid()) return;
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), ConvertUtils.toVec3(velocity));
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0);
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}