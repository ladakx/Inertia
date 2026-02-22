package com.ladakx.inertia.rendering.staticent;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.features.items.ItemRegistry;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

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
                    applyCommonSettings(display, def);
                    yield display;
                }
                yield spawnArmorStandFallback(location, def, true);
            }
            case ITEM_DISPLAY -> {
                Entity display = trySpawnByTypeName(location, "ITEM_DISPLAY");
                if (display != null && applyItemDisplaySettings(display, def, leftRotation)) {
                    applyCommonSettings(display, def);
                    yield display;
                }
                yield spawnArmorStandFallback(location, def, false);
            }
            case ARMOR_STAND -> spawnArmorStandFallback(location, def, false);
            case BOAT -> {
                Entity boat = spawnBoat(location, def);
                if (boat != null) {
                    applyBoatSettings(boat, def);
                    applyCommonSettings(boat, def);
                }
                yield boat;
            }
            case SHULKER -> {
                Entity shulker = trySpawnByTypeName(location, "SHULKER");
                if (shulker != null) {
                    applyShulkerSettings(shulker, def);
                    applyCommonSettings(shulker, def);
                }
                yield shulker;
            }
            case INTERACTION -> {
                Entity interaction = trySpawnByTypeName(location, "INTERACTION");
                if (interaction != null) {
                    applyInteractionSettings(interaction, def);
                    applyCommonSettings(interaction, def);
                    yield interaction;
                }
                // Fallback for older versions without Interaction entity
                yield spawnArmorStandFallback(location, def, false);
            }
        };
    }

    private @Nullable Entity spawnBoat(Location location, RenderEntityDefinition def) {
        // Modern versions use per-wood entity types: OAK_BOAT / OAK_CHEST_BOAT etc.
        String wood = RenderSettings.getString(def.settings(), "boat.type");
        if (wood == null) wood = RenderSettings.getString(def.settings(), "type");
        if (wood == null || wood.isBlank()) wood = "OAK";

        Boolean chest = RenderSettings.getBoolean(def.settings(), "boat.chest");
        if (chest == null) chest = RenderSettings.getBoolean(def.settings(), "chest");
        boolean isChest = chest != null && chest;

        String resolved = wood.trim().toUpperCase(Locale.ROOT) + (isChest ? "_CHEST_BOAT" : "_BOAT");

        Entity boat = trySpawnByTypeName(location, resolved);
        if (boat != null) return boat;

        // Fallbacks for older versions / alternative enums.
        boat = trySpawnByTypeName(location, "BOAT");
        if (boat != null) return boat;

        return trySpawnByTypeName(location, "OAK_BOAT");
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
            applyCommonSettings(stand, def);
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

    private void applyCommonSettings(Entity entity, RenderEntityDefinition def) {
        if (entity == null) return;
        var settings = def.settings();
        if (settings == null || settings.isEmpty()) return;

        Boolean silent = RenderSettings.getBoolean(settings, "silent");
        if (silent != null) applyOptionalBoolean(entity, "setSilent", silent);

        Boolean gravity = RenderSettings.getBoolean(settings, "gravity");
        if (gravity != null) applyOptionalBoolean(entity, "setGravity", gravity);

        Boolean invulnerable = RenderSettings.getBoolean(settings, "invulnerable");
        if (invulnerable != null) applyOptionalBoolean(entity, "setInvulnerable", invulnerable);

        Boolean glowing = RenderSettings.getBoolean(settings, "glowing");
        if (glowing != null) applyOptionalBoolean(entity, "setGlowing", glowing);

        Boolean collidable = RenderSettings.getBoolean(settings, "collidable");
        if (collidable != null) applyOptionalBoolean(entity, "setCollidable", collidable);

        Boolean persistent = RenderSettings.getBoolean(settings, "persistent");
        if (persistent != null) {
            // Available on Mob (not on base Entity in 1.16 API), so use reflection.
            try {
                Method m = entity.getClass().getMethod("setRemoveWhenFarAway", boolean.class);
                m.invoke(entity, !persistent);
            } catch (Throwable ignored) {
            }
        }

        String name = RenderSettings.getString(settings, "custom-name");
        if (name != null && !name.isBlank()) {
            try {
                Method m = Entity.class.getMethod("setCustomName", String.class);
                m.invoke(entity, name);
            } catch (Throwable ignored) {
            }
        }
        Boolean visible = RenderSettings.getBoolean(settings, "custom-name-visible");
        if (visible != null) applyOptionalBoolean(entity, "setCustomNameVisible", visible);
    }

    private void applyOptionalBoolean(Object target, String methodName, boolean value) {
        try {
            Method m = target.getClass().getMethod(methodName, boolean.class);
            m.invoke(target, value);
        } catch (Throwable ignored) {
        }
    }

    private void applyBoatSettings(Entity entity, RenderEntityDefinition def) {
        if (!(entity instanceof Boat boat)) return;
        String typeRaw = RenderSettings.getString(def.settings(), "boat.type");
        if (typeRaw == null) typeRaw = RenderSettings.getString(def.settings(), "type");
        if (typeRaw != null && !typeRaw.isBlank()) {
            applyBoatTypeReflectively(boat, typeRaw);
        }
    }

    private void applyBoatTypeReflectively(Boat boat, String typeRaw) {
        String normalized = typeRaw.trim().toUpperCase(Locale.ROOT);

        // Prefer modern API: setBoatType(Boat.Type)
        if (trySetEnumParam(boat, "setBoatType", normalized)) return;

        // Legacy API: setWoodType(TreeSpecies) (very old versions)
        trySetEnumParam(boat, "setWoodType", normalized);
    }

    private boolean trySetEnumParam(Object target, String methodName, String enumConstantName) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                Class<?> paramType = method.getParameterTypes()[0];
                if (!paramType.isEnum()) continue;

                @SuppressWarnings({"unchecked", "rawtypes"})
                Object enumValue = Enum.valueOf((Class<? extends Enum>) paramType, enumConstantName);
                method.invoke(target, enumValue);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void applyShulkerSettings(Entity entity, RenderEntityDefinition def) {
        if (!(entity instanceof Shulker shulker)) return;
        String colorRaw = RenderSettings.getString(def.settings(), "shulker.color");
        if (colorRaw == null) colorRaw = RenderSettings.getString(def.settings(), "color");
        if (colorRaw != null && !colorRaw.isBlank()) {
            try {
                // DyeColor exists on 1.16 API
                org.bukkit.DyeColor color = org.bukkit.DyeColor.valueOf(colorRaw.trim().toUpperCase(Locale.ROOT));
                shulker.setColor(color);
            } catch (Throwable ignored) {
            }
        }
        Float peekRaw = RenderSettings.getFloat(def.settings(), "shulker.peek");
        if (peekRaw == null) peekRaw = RenderSettings.getFloat(def.settings(), "peek");
        if (peekRaw != null) {
            try {
                Method m = shulker.getClass().getMethod("setPeek", float.class);
                m.invoke(shulker, peekRaw);
            } catch (Throwable ignored) {
            }
        }
        Boolean aiRaw = RenderSettings.getBoolean(def.settings(), "shulker.ai");
        if (aiRaw == null) aiRaw = RenderSettings.getBoolean(def.settings(), "ai");
        if (aiRaw != null) {
            try {
                Method m = shulker.getClass().getMethod("setAI", boolean.class);
                m.invoke(shulker, aiRaw);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyInteractionSettings(Entity entity, RenderEntityDefinition def) {
        // org.bukkit.entity.Interaction is not present on 1.16 API, so use reflection.
        try {
            Class<?> interactionClass = Class.forName("org.bukkit.entity.Interaction");
            if (!interactionClass.isInstance(entity)) return;

            Float width = RenderSettings.getFloat(def.settings(), "interaction.width");
            if (width == null) width = RenderSettings.getFloat(def.settings(), "width");
            if (width != null) invokeIfPresent(interactionClass, entity, "setInteractionWidth", float.class, width);

            Float height = RenderSettings.getFloat(def.settings(), "interaction.height");
            if (height == null) height = RenderSettings.getFloat(def.settings(), "height");
            if (height != null) invokeIfPresent(interactionClass, entity, "setInteractionHeight", float.class, height);

            Boolean responsive = RenderSettings.getBoolean(def.settings(), "interaction.responsive");
            if (responsive == null) responsive = RenderSettings.getBoolean(def.settings(), "responsive");
            if (responsive != null) invokeIfPresent(interactionClass, entity, "setResponsive", boolean.class, responsive);
        } catch (Throwable ignored) {
        }
    }
}
