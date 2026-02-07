package com.ladakx.inertia.rendering.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.rendering.NetworkEntityTracker;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PhysicsDisplayComposite {

    public record DisplayPart(
            RenderEntityDefinition definition,
            NetworkVisual visual
    ) {
        public DisplayPart {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(visual);
        }
    }

    private final AbstractPhysicsBody owner; // Ссылка на владельца для регистрации ID
    private final Body body;
    private final RenderModelDefinition model;
    private final List<DisplayPart> parts;
    private final World world;

    // Cache objects to reduce GC
    private final Vector3f cacheBodyPos = new Vector3f();
    private final Quaternionf cacheBodyRot = new Quaternionf();
    private final Vector3f cacheCenterOffset = new Vector3f();
    private final Vector3f cacheFinalPos = new Vector3f();
    private final Vector3f cacheLocalOffset = new Vector3f();
    private final Quaternionf cacheFinalRot = new Quaternionf();

    public PhysicsDisplayComposite(AbstractPhysicsBody owner, RenderModelDefinition model, World world, List<DisplayPart> parts) {
        this.owner = Objects.requireNonNull(owner);
        this.body = owner.getBody();
        this.model = Objects.requireNonNull(model);
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);

        // Initial Registration
        registerAll();
    }

    private void registerAll() {
        NetworkEntityTracker tracker = InertiaPlugin.getInstance().getNetworkEntityTracker();
        if (tracker == null) return;

        RVec3 pos = body.getPosition();
        Quat rot = body.getRotation();

        Location baseLoc = new Location(world, pos.xx(), pos.yy(), pos.zz());
        Quaternionf baseRot = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());

        for (DisplayPart part : parts) {
            int visualId = part.visual().getId();

            // 1. Регистрируем в Трекере (для видимости)
            tracker.register(part.visual(), baseLoc, baseRot);

            // 2. Регистрируем в Физическом Мире (для кликов/взаимодействия)
            owner.getSpace().registerNetworkEntityId(owner, visualId);
        }
    }

    public void capture(boolean sleeping, RVec3 origin, List<VisualState> accumulator, SnapshotPool pool) {
        if (parts.isEmpty()) return;

        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        cacheCenterOffset.set(0, 0, 0);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            NetworkVisual visual = part.visual();

            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            if (!visible) continue;

            cacheFinalPos.set(cacheBodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                cacheBodyRot.transform(cacheLocalOffset);
                cacheFinalPos.add(cacheLocalOffset);
            }

            if (model.syncRotation()) {
                cacheFinalRot.set(cacheBodyRot).mul(def.localRotation());
            } else {
                cacheFinalRot.set(def.localRotation());
            }

            VisualState state = pool.borrowState();
            state.set(
                    visual,
                    cacheFinalPos,
                    cacheFinalRot,
                    cacheCenterOffset,
                    def.rotTranslation(),
                    visible
            );
            accumulator.add(state);
        }
    }

    public void setGlowing(boolean glowing) {
        for (DisplayPart part : parts) {
            part.visual().setGlowing(glowing);
        }
    }

    public void markAsStatic(@Nullable UUID clusterId) {
        if (parts.isEmpty()) return;

        boolean sleeping = !body.isActive();
        RVec3 origin = owner.getSpace().getOrigin();

        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        Location spawnLoc = new Location(world, 0, 0, 0);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            NetworkVisual visual = part.visual();

            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            if (!visible) {
                cleanupNetworkVisual(visual);
                continue;
            }

            cacheFinalPos.set(cacheBodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                cacheBodyRot.transform(cacheLocalOffset);
                cacheFinalPos.add(cacheLocalOffset);
            }

            if (model.syncRotation()) {
                cacheFinalRot.set(cacheBodyRot).mul(def.localRotation());
            } else {
                cacheFinalRot.set(def.localRotation());
            }

            spawnLoc.setX(cacheFinalPos.x);
            spawnLoc.setY(cacheFinalPos.y);
            spawnLoc.setZ(cacheFinalPos.z);

            try {
                Entity spawned = spawnStaticEntity(spawnLoc, def, cacheFinalRot);
                if (spawned != null) {
                    tagAsStatic(spawned, def, clusterId);
                }
            } catch (Exception ex) {
                InertiaLogger.warn("Failed to spawn static entity for render part " + def.key() + " in world " + world.getName(), ex);
            } finally {
                cleanupNetworkVisual(visual);
            }
        }
    }

    public boolean isValid() {
        return body != null && !parts.isEmpty();
    }

    public void destroy() {
        NetworkEntityTracker tracker = InertiaPlugin.getInstance().getNetworkEntityTracker();
        if (tracker != null) {
            for (DisplayPart part : parts) {
                int visualId = part.visual().getId();

                // 1. Убираем из трекера
                tracker.unregister(part.visual());

                // 2. Убираем маппинг ID -> Body
                if (owner.isValid()) { // Проверка на всякий случай, хотя при destroy owner еще жив
                    owner.getSpace().unregisterNetworkEntityId(visualId);
                }

                // Force destroy packet
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    part.visual().destroyFor(p);
                }
            }
        }
    }

    private void cleanupNetworkVisual(NetworkVisual visual) {
        NetworkEntityTracker tracker = InertiaPlugin.getInstance().getNetworkEntityTracker();
        if (tracker != null) {
            tracker.unregister(visual);
        }

        try {
            owner.getSpace().unregisterNetworkEntityId(visual.getId());
        } catch (Exception ignored) {
            // Owner/space may already be shutting down
        }

        // Extra safety: force destroy packet to all online players (in case tracker visibility got out of sync)
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                visual.destroyFor(p);
            } catch (Exception ignored) {
            }
        }
    }

    private void tagAsStatic(Entity entity, RenderEntityDefinition def, @Nullable UUID clusterId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING, owner.getBodyId());
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING, owner.getUuid().toString());
        pdc.set(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING, "true");
        if (clusterId != null) {
            pdc.set(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING, clusterId.toString());
        }
        pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ID, PersistentDataType.STRING, model.id());
        pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ENTITY_ID, PersistentDataType.STRING, def.key());
    }

    private @Nullable Entity spawnStaticEntity(Location location, RenderEntityDefinition def, Quaternionf leftRotation) {
        if (location.getWorld() == null) return null;

        return switch (def.kind()) {
            case BLOCK_DISPLAY -> {
                Entity display = trySpawnByTypeName(location, "BLOCK_DISPLAY");
                if (display != null && applyBlockDisplaySettings(display, def, leftRotation)) {
                    yield display;
                }
                yield spawnArmorStandFallback(location, def, true);
            }
            case ITEM_DISPLAY -> {
                Entity display = trySpawnByTypeName(location, "ITEM_DISPLAY");
                if (display != null && applyItemDisplaySettings(display, def, leftRotation)) {
                    yield display;
                }
                yield spawnArmorStandFallback(location, def, false);
            }
            case ARMOR_STAND -> spawnArmorStandFallback(location, def, false);
        };
    }

    private @Nullable Entity trySpawnByTypeName(Location location, String entityTypeName) {
        try {
            EntityType type = EntityType.valueOf(entityTypeName);
            return location.getWorld().spawnEntity(location, type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private @Nullable Entity spawnArmorStandFallback(Location location, RenderEntityDefinition def, boolean asBlockHelmet) {
        try {
            Entity entity = location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            if (!(entity instanceof ArmorStand stand)) {
                return entity;
            }

            stand.setSmall(def.small());
            stand.setInvisible(def.invisible());
            stand.setMarker(def.marker());
            stand.setBasePlate(def.basePlate());
            stand.setArms(def.arms());

            ItemStack item = resolveItem(def, asBlockHelmet);
            if (item != null) {
                stand.getEquipment().setHelmet(item);
            }

            // Make sure it doesn't despawn
            stand.setRemoveWhenFarAway(false);
            return stand;
        } catch (Throwable ex) {
            InertiaLogger.warn("Failed to spawn ArmorStand fallback for static render part " + def.key() + " in world " + world.getName(), ex);
            return null;
        }
    }

    private @Nullable ItemStack resolveItem(RenderEntityDefinition def, boolean asBlockHelmet) {
        try {
            if (asBlockHelmet) {
                if (def.blockType() == null) return null;
                return new ItemStack(def.blockType());
            }
            if (def.itemModelKey() == null) return null;
            return InertiaPlugin.getInstance().getItemRegistry().getItem(def.itemModelKey());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean applyBlockDisplaySettings(Entity entity, RenderEntityDefinition def, Quaternionf leftRotation) {
        if (!applyDisplayTransformation(entity, def, leftRotation)) {
            return false;
        }

        try {
            Object blockDisplay = entity;
            Class<?> blockDisplayClass = Class.forName("org.bukkit.entity.BlockDisplay");
            if (!blockDisplayClass.isInstance(blockDisplay)) {
                return false;
            }

            org.bukkit.Material material = def.blockType() != null ? def.blockType() : org.bukkit.Material.STONE;
            Object blockData = material.createBlockData();

            Method setBlock = blockDisplayClass.getMethod("setBlock", Class.forName("org.bukkit.block.data.BlockData"));
            setBlock.invoke(blockDisplay, blockData);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean applyItemDisplaySettings(Entity entity, RenderEntityDefinition def, Quaternionf leftRotation) {
        if (!applyDisplayTransformation(entity, def, leftRotation)) {
            return false;
        }

        try {
            Object itemDisplay = entity;
            Class<?> itemDisplayClass = Class.forName("org.bukkit.entity.ItemDisplay");
            if (!itemDisplayClass.isInstance(itemDisplay)) {
                return false;
            }

            ItemStack item = resolveItem(def, false);
            if (item != null) {
                Method setItemStack = itemDisplayClass.getMethod("setItemStack", ItemStack.class);
                setItemStack.invoke(itemDisplay, item);
            }

            applyItemDisplayTransform(itemDisplayClass, itemDisplay, def);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyItemDisplayTransform(Class<?> itemDisplayClass, Object itemDisplay, RenderEntityDefinition def) {
        if (def.displayMode() == null) return;

        try {
            Class<?> transformEnum = Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object transform = Enum.valueOf((Class<? extends Enum>) transformEnum, convertDisplayModeName(def.displayMode().name()));
            Method setter;
            try {
                setter = itemDisplayClass.getMethod("setItemDisplayTransform", transformEnum);
            } catch (NoSuchMethodException e) {
                setter = itemDisplayClass.getMethod("setItemTransform", transformEnum);
            }
            setter.invoke(itemDisplay, transform);
        } catch (Throwable ignored) {
        }
    }

    private String convertDisplayModeName(String inertiaModeName) {
        // InertiaDisplayMode names match modern Bukkit enum names for ItemDisplay transforms
        // except "NONE" which is usually "NONE".
        return inertiaModeName;
    }

    private boolean applyDisplayTransformation(Entity entity, RenderEntityDefinition def, Quaternionf leftRotation) {
        try {
            Class<?> displayClass = Class.forName("org.bukkit.entity.Display");
            if (!displayClass.isInstance(entity)) {
                return false;
            }

            Vector scale = def.scale();
            Vector3f scaleVec = new Vector3f((float) scale.getX(), (float) scale.getY(), (float) scale.getZ());
            Vector3f translationVec = computeDisplayTranslation(def, scaleVec, leftRotation);

            Quaternionf rightRotation = new Quaternionf(def.localRotation());
            Quaternionf leftRotationCopy = new Quaternionf(leftRotation);

            Class<?> transformationClass = Class.forName("org.bukkit.util.Transformation");
            Constructor<?> ctor = transformationClass.getConstructor(Vector3f.class, Quaternionf.class, Vector3f.class, Quaternionf.class);
            Object transformation = ctor.newInstance(translationVec, leftRotationCopy, scaleVec, rightRotation);

            Method setTransformation = displayClass.getMethod("setTransformation", transformationClass);
            setTransformation.invoke(entity, transformation);

            applyOptionalDisplaySettings(displayClass, entity, def);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Vector3f computeDisplayTranslation(RenderEntityDefinition def, Vector3f scaleVec, Quaternionf leftRotation) {
        Vector translation = def.translation();
        Vector localOffset = def.localOffset();

        if (def.kind() == RenderEntityDefinition.EntityKind.BLOCK_DISPLAY) {
            Vector3f center = new Vector3f(-0.5f, -0.5f, -0.5f).mul(scaleVec);
            Vector3f originalTrans = new Vector3f(
                    (float) translation.getX(),
                    (float) translation.getY(),
                    (float) translation.getZ()
            ).add(
                    (float) localOffset.getX(),
                    (float) localOffset.getY(),
                    (float) localOffset.getZ()
            );

            if (def.rotTranslation()) {
                return center.add(originalTrans).rotate(leftRotation);
            }
            return new Vector3f(center).rotate(leftRotation).add(originalTrans);
        }

        return new Vector3f(
                (float) translation.getX(),
                (float) translation.getY(),
                (float) translation.getZ()
        );
    }

    private void applyOptionalDisplaySettings(Class<?> displayClass, Object display, RenderEntityDefinition def) {
        invokeIfPresent(displayClass, display, "setViewRange", float.class, def.viewRange());
        invokeIfPresent(displayClass, display, "setShadowRadius", float.class, def.shadowRadius());
        invokeIfPresent(displayClass, display, "setShadowStrength", float.class, def.shadowStrength());
        invokeIfPresent(displayClass, display, "setInterpolationDuration", int.class, def.interpolationDuration());
        invokeIfPresent(displayClass, display, "setTeleportDuration", int.class, def.teleportDuration());

        if (def.billboard() != null) {
            try {
                Class<?> billboardEnum = Class.forName("org.bukkit.entity.Display$Billboard");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object billboard = Enum.valueOf((Class<? extends Enum>) billboardEnum, def.billboard().name());
                Method setBillboard = displayClass.getMethod("setBillboard", billboardEnum);
                setBillboard.invoke(display, billboard);
            } catch (Throwable ignored) {
            }
        }
    }

    private void invokeIfPresent(Class<?> targetClass, Object target, String methodName, Class<?> paramType, Object value) {
        if (value == null) return;
        try {
            Method method = targetClass.getMethod(methodName, paramType);
            method.invoke(target, value);
        } catch (Throwable ignored) {
        }
    }
}
