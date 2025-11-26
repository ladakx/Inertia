package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.registry.PhysicsModelRegistry;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Фізичний об'єкт, який повністю конфігурується через bodies.yml + render.yml.
 * Тепер використовує RenderFactory для підтримки мультиверсійності.
 */
public class MinecraftPhysicsObject extends AbstractPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;

    public MinecraftPhysicsObject(@NotNull MinecraftSpace space,
                                  @NotNull String bodyId,
                                  @NotNull PhysicsModelRegistry modelRegistry,
                                  @NotNull RenderFactory renderFactory,
                                  @NotNull RVec3 initialPosition,
                                  @NotNull Quat initialRotation) {
        super(space, createBodySettings(bodyId, modelRegistry, initialPosition, initialRotation));
        this.bodyId = bodyId;

        PhysicsModelRegistry.BodyModel model = modelRegistry.require(bodyId);
        Optional<RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isPresent()) {
            RenderModelDefinition renderDef = renderOpt.get();
            World world = space.getWorldBukkit();

            // Створюємо початкову локацію для спавну візуалів
            Location spawnLocation = new Location(world, initialPosition.xx(), initialPosition.yy(), initialPosition.zz());

            // Збираємо частини композиту
            List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
            for (RenderEntityDefinition entityDef : renderDef.entities().values()) {
                // Фабрика створює конкретну реалізацію (ArmorStand або Display)
                VisualObject visual = renderFactory.create(world, spawnLocation, entityDef);

                if (visual.isValid()) {
                    parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
                }
            }

            // Створюємо композит, передаючи світ та частини
            this.displayComposite = new PhysicsDisplayComposite(getBody(), renderDef, world, parts);

            // Одразу оновлюємо позиції, щоб врахувати offset-и частин
            this.displayComposite.update();
        } else {
            this.displayComposite = null;
        }
    }

    private static BodyCreationSettings createBodySettings(String bodyId,
                                                           PhysicsModelRegistry modelRegistry,
                                                           RVec3 initialPosition,
                                                           Quat initialRotation) {
        PhysicsModelRegistry.BodyModel model = modelRegistry.require(bodyId);
        BodyDefinition def = model.bodyDefinition();
        BodyPhysicsSettings phys = def.physicsSettings();

        List<String> shapeLines = def.shapeLines();
        ConstShape shape = JShapeFactory.createShape(shapeLines);

        BodyCreationSettings settings = new BodyCreationSettings()
                .setShape(shape)
                .setMotionType(phys.motionType())
                .setObjectLayer(phys.objectLayer())
                .setLinearDamping(phys.linearDamping())
                .setAngularDamping(phys.angularDamping());

        settings.getMassProperties().setMass(phys.mass());
        settings.setFriction(phys.friction());
        settings.setRestitution(phys.restitution());

        settings.setPosition(initialPosition);
        settings.setRotation(initialRotation);

        return settings;
    }

    @Override
    public @Nullable PhysicsDisplayComposite getDisplay() {
        return displayComposite;
    }

    @Override
    public void update() {
        if (displayComposite != null) {
            displayComposite.update();
        }
    }

    public void destroy() {
        if (displayComposite != null) {
            displayComposite.destroy();
        }
    }

    public String getBodyId() {
        return bodyId;
    }
}