package com.ladakx.inertia.rendering.staticent;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.features.items.ItemRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
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

public final class BukkitStaticEntityPersister implements StaticEntityPersister {

    private final ItemRegistry itemRegistry;

    public BukkitStaticEntityPersister(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    @Override
    public @Nullable Entity persist(Location location, RenderEntityDefinition definition, Quaternionf leftRotation, StaticEntityMetadata metadata) {
        try {
            Entity spawned = spawnStaticEntity(location, definition, leftRotation);
            if (spawned == null) {
                return null;
            }
            tagAsStatic(spawned, metadata);
            return spawned;
        } catch (Exception ex) {
            InertiaLogger.warn("Failed to persist static entity for render part " + definition.key()
                    + " in world " + (location.getWorld() != null ? location.getWorld().getName() : "null"), ex);
            return null;
        }
    }

    private void tagAsStatic(Entity entity, StaticEntityMetadata metadata) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_ID, PersistentDataType.STRING, metadata.bodyId());
        pdc.set(InertiaPDCKeys.INERTIA_PHYSICS_BODY_UUID, PersistentDataType.STRING, metadata.bodyUuid().toString());
        pdc.set(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING, "true");
        if (metadata.clusterId() != null) {
            pdc.set(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING, metadata.clusterId().toString());
        }
        pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ID, PersistentDataType.STRING, metadata.renderModelId());
        pdc.set(InertiaPDCKeys.INERTIA_RENDER_MODEL_ENTITY_ID, PersistentDataType.STRING, metadata.renderEntityKey());
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

            stand.setRemoveWhenFarAway(false);
            return stand;
        } catch (Throwable ex) {
            InertiaLogger.warn("Failed to spawn ArmorStand fallback for static render part " + def.key(), ex);
            return null;
        }
    }

    private @Nullable ItemStack resolveItem(RenderEntityDefinition def, boolean asBlockHelmet) {
        if (asBlockHelmet) {
            Material type = def.blockType();
            return type != null ? new ItemStack(type) : null;
        }
        if (def.itemModelKey() == null || itemRegistry == null) return null;
        return itemRegistry.getItem(def.itemModelKey());
    }

    private boolean applyBlockDisplaySettings(Entity entity, RenderEntityDefinition def, Quaternionf leftRotation) {
        if (!applyDisplayTransformation(entity, def, leftRotation)) {
            return false;
        }

        try {
            Class<?> blockDisplayClass = Class.forName("org.bukkit.entity.BlockDisplay");
            if (!blockDisplayClass.isInstance(entity)) {
                return false;
            }

            Material material = def.blockType() != null ? def.blockType() : Material.STONE;
            Object blockData = material.createBlockData();

            Method setBlock = blockDisplayClass.getMethod("setBlock", Class.forName("org.bukkit.block.data.BlockData"));
            setBlock.invoke(entity, blockData);
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
            Class<?> itemDisplayClass = Class.forName("org.bukkit.entity.ItemDisplay");
            if (!itemDisplayClass.isInstance(entity)) {
                return false;
            }

            ItemStack item = resolveItem(def, false);
            if (item != null) {
                Method setItemStack = itemDisplayClass.getMethod("setItemStack", ItemStack.class);
                setItemStack.invoke(entity, item);
            }

            applyItemDisplayTransform(itemDisplayClass, entity, def);
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
            Object transform = Enum.valueOf((Class<? extends Enum>) transformEnum, def.displayMode().name());
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

