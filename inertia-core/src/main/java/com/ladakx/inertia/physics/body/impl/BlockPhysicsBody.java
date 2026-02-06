package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BlockPhysicsBody extends DisplayedPhysicsBody {

    private final String bodyId;
    private boolean removed = false;

    public BlockPhysicsBody(@NotNull PhysicsWorld space,
                            @NotNull String bodyId,
                            @NotNull PhysicsBodyRegistry modelRegistry,
                            @NotNull RenderFactory renderFactory,
                            @NotNull JShapeFactory shapeFactory,
                            @NotNull RVec3 initialPosition,
                            @NotNull Quat initialRotation) {
        super(space, createBodySettings(bodyId, modelRegistry, shapeFactory, initialPosition, initialRotation), renderFactory, modelRegistry);
        this.bodyId = bodyId;
        this.displayComposite = recreateDisplay();
    }

    @Override
    protected PhysicsDisplayComposite recreateDisplay() {
        PhysicsBodyRegistry.BodyModel model = modelRegistry.require(bodyId);
        Optional<RenderModelDefinition> renderOpt = model.renderModel();
        if (renderOpt.isPresent()) {
            RenderModelDefinition renderDef = renderOpt.get();
            World world = getSpace().getWorldBukkit();
            RVec3 currentPos = getBody().getPosition();
            Location spawnLocation = new Location(world, currentPos.xx(), currentPos.yy(), currentPos.zz());

            List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
            for (Map.Entry<String, RenderEntityDefinition> entry : renderDef.entities().entrySet()) {
                String entityKey = entry.getKey();
                RenderEntityDefinition entityDef = entry.getValue();
                NetworkVisual visual = renderFactory.create(world, spawnLocation, entityDef);
                parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
            }
            return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
        }
        return null;
    }

    private static BodyCreationSettings createBodySettings(String bodyId,
                                                           PhysicsBodyRegistry modelRegistry,
                                                           JShapeFactory shapeFactory,
                                                           RVec3 initialPosition,
                                                           Quat initialRotation) {
        PhysicsBodyRegistry.BodyModel model = modelRegistry.require(bodyId);
        BlockBodyDefinition def = (BlockBodyDefinition) model.bodyDefinition();
        BodyPhysicsSettings phys = def.physicsSettings();
        List<String> shapeLines = def.shapeLines();
        ConstShape shape = shapeFactory.createShape(shapeLines);

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
        settings.setGravityFactor(phys.gravityFactor());
        settings.setPosition(initialPosition);
        settings.setRotation(initialRotation);

        return settings;
    }

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.BLOCK;
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy();
    }
}
