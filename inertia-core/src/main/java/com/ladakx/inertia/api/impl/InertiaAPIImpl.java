package com.ladakx.inertia.api.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.api.body.InertiaPhysicsObject;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.jolt.object.BlockPhysicsObject;
import com.ladakx.inertia.jolt.object.PhysicsObjectType;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.nms.render.RenderFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Реалізація InertiaAPI.
 * Приховує деталі Jolt та NMS від зовнішніх плагінів.
 */
public class InertiaAPIImpl extends InertiaAPI {

    private final InertiaPlugin plugin;
    private final SpaceManager spaceManager;
    private final PhysicsBodyRegistry modelRegistry;
    private final RenderFactory renderFactory;

    public InertiaAPIImpl(InertiaPlugin plugin) {
        this.plugin = plugin;
        this.spaceManager = SpaceManager.getInstance();
        this.modelRegistry = ConfigManager.getInstance().getPhysicsBodyRegistry();
        this.renderFactory = plugin.getRenderFactory();
    }

    @Override
    public @Nullable InertiaPhysicsObject createBody(@NotNull Location location, @NotNull String bodyId) {
        if (location.getWorld() == null) {
            InertiaLogger.warn("Cannot create body: Location world is null.");
            return null;
        }

        // 1. Отримуємо фізичний простір
        MinecraftSpace space = spaceManager.getSpace(location.getWorld());
        if (space == null) {
            // Світ не є фізичним (немає в worlds.yml або ще не завантажений)
            return null;
        }

        // 2. Перевіряємо наявність моделі
        if (modelRegistry.find(bodyId).isEmpty()) {
            InertiaLogger.warn("Cannot create body: Body ID '" + bodyId + "' not found in registry.");
            return null;
        }

        // 3. Конвертуємо координати
        // Для Jolt RVec3 (Double Precision)
        RVec3 initialPos = new RVec3(location.getX(), location.getY(), location.getZ());
        
        // Конвертація Bukkit Yaw/Pitch -> Quaternion
        float yawRad = (float) Math.toRadians(-location.getYaw());
        float pitchRad = (float) Math.toRadians(location.getPitch());

        Quaternionf jomlQuat = new Quaternionf().rotationYXZ(yawRad, pitchRad, 0f);
        Quat initialRot = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);
//        Quat initialRot = new Quat(0, 0, 0, 1);

        PhysicsObjectType type = modelRegistry.require(bodyId).bodyDefinition().type();

        try {
            // 4. Створюємо об'єкт
            // MinecraftPhysicsObject автоматично додає себе до Space та Jolt System у конструкторі
            if (type == PhysicsObjectType.BLOCK) {
                BlockPhysicsObject object = new BlockPhysicsObject(
                        space,
                        bodyId,
                        modelRegistry,
                        renderFactory,
                        initialPos,
                        initialRot
                );
                return object;
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
        return ConfigManager.getInstance().getWorldsConfig().getWorldSettings(worldName) != null;
    }
}