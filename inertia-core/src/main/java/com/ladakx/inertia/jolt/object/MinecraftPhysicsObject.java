package com.ladakx.inertia.jolt.object;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.api.body.InertiaPhysicsObject; // Імпорт з API
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
 * Фізичний об'єкт, який повністю конфігурується через bodies.yml + render.yml.
 * Реалізує InertiaPhysicsObject для зовнішнього API.
 */
public class MinecraftPhysicsObject extends AbstractPhysicsObject implements InertiaPhysicsObject {

    private final String bodyId;
    private final PhysicsDisplayComposite displayComposite;
    private boolean removed = false;

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
        BodyDefinition def = model.bodyDefinition();
        BodyPhysicsSettings phys = def.physicsSettings();

        List<String> shapeLines = def.shapeLines();
        ConstShape shape = JShapeFactory.createShape(shapeLines);

        InertiaLogger.debug("Creating body '" + bodyId + "' with shape: " + shapeLines);
        InertiaLogger.debug("Shape details: " +
                "type=" + shape.getType() +
                ", centerOfMass=" + shape.getCenterOfMass() +
                ", type=" + shape.getType()
        );
        InertiaLogger.debug("AaBox: min=" + shape.getLocalBounds().getMin() + ", max=" + shape.getLocalBounds().getMax());
        InertiaLogger.debug("Body physics: " +
                "mass=" + phys.mass() +
                ", motionType=" + phys.motionType() +
                ", objectLayer=" + phys.objectLayer() +
                ", linearDamping=" + phys.linearDamping() +
                ", angularDamping=" + phys.angularDamping() +
                ", friction=" + phys.friction() +
                ", restitution=" + phys.restitution()
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
        getSpace().removeObject(this);
        getSpace().getBodyInterface().removeBody(getBody().getId());
        getSpace().getBodyInterface().destroyBody(getBody().getId());

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
    public void remove() {
        destroy();
    }

    @Override
    public boolean isValid() {
        return !removed && !getBody().isActive();
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
        Vec3 joltVel = ConvertUtils.toJolt(velocity);
        getSpace().getBodyInterface().setLinearVelocity(getBody().getId(), joltVel);
    }

    @Override
    public @NotNull Location getLocation() {
        if (!isValid()) return new Location(getSpace().getWorldBukkit(), 0, 0, 0); // Fallback
        RVec3 pos = getBody().getPosition();
        return new Location(getSpace().getWorldBukkit(), pos.xx(), pos.yy(), pos.zz());
    }
}