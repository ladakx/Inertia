package com.ladakx.inertia.core.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.physics.body.impl.BlockPhysicsBody;
import com.ladakx.inertia.physics.body.PhysicsBodyType;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class InertiaAPIImpl extends InertiaAPI {

    private final InertiaPlugin plugin;
    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final ConfigurationService configurationService;
    private final RenderFactory renderFactory;

    // Внедряем зависимости через конструктор
    public InertiaAPIImpl(InertiaPlugin plugin, PhysicsWorldRegistry physicsWorldRegistry, ConfigurationService configurationService) {
        this.plugin = plugin;
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.configurationService = configurationService;
        this.renderFactory = plugin.getRenderFactory();
    }

    @Override
    public @Nullable InertiaPhysicsBody createBody(@NotNull Location location, @NotNull String bodyId) {
        if (location.getWorld() == null) {
            InertiaLogger.warn("Cannot create body: Location world is null.");
            return null;
        }

        PhysicsWorld space = physicsWorldRegistry.getSpace(location.getWorld());
        if (space == null) {
            return null;
        }

        // Используем configManager вместо статики
        PhysicsBodyRegistry modelRegistry = configurationService.getPhysicsBodyRegistry();

        if (modelRegistry.find(bodyId).isEmpty()) {
            InertiaLogger.warn("Cannot create body: Body ID '" + bodyId + "' not found in registry.");
            return null;
        }

        RVec3 initialPos = new RVec3(location.getX(), location.getY(), location.getZ());
        float yawRad = (float) Math.toRadians(-location.getYaw());
        float pitchRad = (float) Math.toRadians(location.getPitch());
        Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
        Quat initialRot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        PhysicsBodyType type = modelRegistry.require(bodyId).bodyDefinition().type();

        try {
            if (type == PhysicsBodyType.BLOCK) {
                return new BlockPhysicsBody(
                        space,
                        bodyId,
                        modelRegistry,
                        renderFactory,
                        initialPos,
                        initialRot
                );
            } else {
                InertiaLogger.warn("Cannot create body: Unsupported body type for ID '" + bodyId + "'.");
                return null;
            }
        } catch (Exception e) {
            InertiaLogger.error("Failed to spawn body '" + bodyId + "' via API", e);
            return null;
        }
    }

    @Override
    public boolean isWorldSimulated(@NotNull String worldName) {
        return configurationService.getWorldsConfig().getWorldSettings(worldName) != null;
    }
}