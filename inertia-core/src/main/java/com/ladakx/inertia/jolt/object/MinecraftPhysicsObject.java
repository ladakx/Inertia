package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.jolt.shape.JShapeFactory;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.physics.config.BodyDefinition;
import com.ladakx.inertia.physics.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.registry.PhysicsModelRegistry;
import com.ladakx.inertia.render.DisplayEntityFactory;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Фізичний об'єкт, який повністю конфігурується через bodies.yml + render.yml.
 */
public class MinecraftPhysicsObject extends AbstractPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;

    public MinecraftPhysicsObject(@NotNull MinecraftSpace space,
                                  @NotNull String bodyId,
                                  @NotNull PhysicsModelRegistry modelRegistry,
                                  @NotNull DisplayEntityFactory displayFactory,
                                  @NotNull RVec3 initialPosition,
                                  @NotNull Quat initialRotation) {
        super(space, createBodySettings(bodyId, modelRegistry, initialPosition, initialRotation));
        this.bodyId = bodyId;

        PhysicsModelRegistry.BodyModel model = modelRegistry.require(bodyId);
        Optional<com.ladakx.inertia.render.config.RenderModelDefinition> renderOpt = model.renderModel();

        if (renderOpt.isPresent()) {
            this.displayComposite = displayFactory.createComposite(
                    renderOpt.get(),
                    space.getWorldBukkit(),
                    getBody()
            );
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