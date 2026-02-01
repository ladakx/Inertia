package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.common.pdc.InertiaPDCUtils;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.factory.shape.JShapeFactory;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.physics.body.config.BlockBodyDefinition;
import com.ladakx.inertia.physics.body.config.BodyPhysicsSettings;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.runtime.PhysicsDisplayComposite;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlockPhysicsBody extends DisplayedPhysicsBody implements InertiaPhysicsBody {

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

            // Используем ТЕКУЩУЮ позицию тела, а не начальную
            RVec3 currentPos = getBody().getPosition();
            Location spawnLocation = new Location(world, currentPos.xx(), currentPos.yy(), currentPos.zz());

            List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();
            UUID bodyUuid = getUuid();

            for (Map.Entry<String, RenderEntityDefinition> entry : renderDef.entities().entrySet()) {
                String entityKey = entry.getKey();
                RenderEntityDefinition entityDef = entry.getValue();

                VisualEntity visual = renderFactory.create(world, spawnLocation, entityDef);
                if (visual.isValid()) {
                    InertiaPDCUtils.applyInertiaTags(
                            visual,
                            bodyId,
                            bodyUuid,
                            renderDef.id(),
                            entityKey
                    );
                    parts.add(new PhysicsDisplayComposite.DisplayPart(entityDef, visual));
                }
            }

            return new PhysicsDisplayComposite(getBody(), renderDef, world, parts);
        }
        return null;
    }

    // --- Static Helpers (без изменений) ---
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

    // --- InertiaPhysicsBody Implementation (API) ---

    @Override
    public @NotNull String getBodyId() {
        return bodyId;
    }

    @Override
    public @NotNull PhysicsBodyType getType() {
        return PhysicsBodyType.BLOCK;
    }

    @Override
    public void remove() {
        destroy();
    }

    @Override
    public void destroy() {
        if (removed) return;
        removed = true;
        super.destroy(); // DisplayComposite destroy is handled in super
    }

    @Override
    public boolean isValid() {
        return !removed && getBody() != null;
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
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), ConvertUtils.toVec3(velocity));
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0);
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}