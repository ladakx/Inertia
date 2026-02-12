package com.ladakx.inertia.nms.v1_21_r8.utils;

import com.ladakx.inertia.common.logging.InertiaLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.util.Optional;

public final class MetadataAccessors {

    public static EntityDataAccessor<Byte> ENTITY_FLAGS;
    public static EntityDataAccessor<Integer> ENTITY_AIR_SUPPLY;
    public static EntityDataAccessor<Optional<Component>> ENTITY_CUSTOM_NAME;
    public static EntityDataAccessor<Boolean> ENTITY_CUSTOM_NAME_VISIBLE;
    public static EntityDataAccessor<Boolean> ENTITY_SILENT;
    public static EntityDataAccessor<Boolean> ENTITY_NO_GRAVITY;
    public static EntityDataAccessor<Pose> ENTITY_POSE;
    public static EntityDataAccessor<Integer> ENTITY_TICKS_FROZEN;

    public static EntityDataAccessor<Integer> DISPLAY_INTERPOLATION_START_DELTA;
    public static EntityDataAccessor<Integer> DISPLAY_INTERPOLATION_DURATION;
    public static EntityDataAccessor<Integer> DISPLAY_TELEPORT_DURATION;
    public static EntityDataAccessor<Vector3fc> DISPLAY_TRANSLATION;
    public static EntityDataAccessor<Vector3fc> DISPLAY_SCALE;
    public static EntityDataAccessor<Quaternionfc> DISPLAY_LEFT_ROTATION;
    public static EntityDataAccessor<Quaternionfc> DISPLAY_RIGHT_ROTATION;
    public static EntityDataAccessor<Byte> DISPLAY_BILLBOARD;
    public static EntityDataAccessor<Integer> DISPLAY_BRIGHTNESS;
    public static EntityDataAccessor<Float> DISPLAY_VIEW_RANGE;
    public static EntityDataAccessor<Float> DISPLAY_SHADOW_RADIUS;
    public static EntityDataAccessor<Float> DISPLAY_SHADOW_STRENGTH;
    public static EntityDataAccessor<Float> DISPLAY_WIDTH;
    public static EntityDataAccessor<Float> DISPLAY_HEIGHT;
    public static EntityDataAccessor<Integer> DISPLAY_GLOW_COLOR;

    public static EntityDataAccessor<BlockState> BLOCK_DISPLAY_STATE;

    public static EntityDataAccessor<ItemStack> ITEM_DISPLAY_ITEM;
    public static EntityDataAccessor<Byte> ITEM_DISPLAY_CONTEXT;

    static {
        try {
            ENTITY_FLAGS = getField(Entity.class, "DATA_SHARED_FLAGS_ID");
            ENTITY_AIR_SUPPLY = getField(Entity.class, "DATA_AIR_SUPPLY_ID");
            ENTITY_CUSTOM_NAME = getField(Entity.class, "DATA_CUSTOM_NAME");
            ENTITY_CUSTOM_NAME_VISIBLE = getField(Entity.class, "DATA_CUSTOM_NAME_VISIBLE");
            ENTITY_SILENT = getField(Entity.class, "DATA_SILENT");
            ENTITY_NO_GRAVITY = getField(Entity.class, "DATA_NO_GRAVITY");
            ENTITY_POSE = getField(Entity.class, "DATA_POSE");
            ENTITY_TICKS_FROZEN = getField(Entity.class, "DATA_TICKS_FROZEN");

            DISPLAY_INTERPOLATION_START_DELTA = getField(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS_ID");
            DISPLAY_INTERPOLATION_DURATION = getField(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID");
            DISPLAY_TELEPORT_DURATION = getField(Display.class, "DATA_POS_ROT_INTERPOLATION_DURATION_ID");
            DISPLAY_TRANSLATION = getField(Display.class, "DATA_TRANSLATION_ID");
            DISPLAY_SCALE = getField(Display.class, "DATA_SCALE_ID");
            DISPLAY_LEFT_ROTATION = getField(Display.class, "DATA_LEFT_ROTATION_ID");
            DISPLAY_RIGHT_ROTATION = getField(Display.class, "DATA_RIGHT_ROTATION_ID");
            DISPLAY_BILLBOARD = getField(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
            DISPLAY_BRIGHTNESS = getField(Display.class, "DATA_BRIGHTNESS_OVERRIDE_ID");
            DISPLAY_VIEW_RANGE = getField(Display.class, "DATA_VIEW_RANGE_ID");
            DISPLAY_SHADOW_RADIUS = getField(Display.class, "DATA_SHADOW_RADIUS_ID");
            DISPLAY_SHADOW_STRENGTH = getField(Display.class, "DATA_SHADOW_STRENGTH_ID");
            DISPLAY_WIDTH = getField(Display.class, "DATA_WIDTH_ID");
            DISPLAY_HEIGHT = getField(Display.class, "DATA_HEIGHT_ID");
            DISPLAY_GLOW_COLOR = getField(Display.class, "DATA_GLOW_COLOR_OVERRIDE_ID");

            BLOCK_DISPLAY_STATE = getField(Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");

            ITEM_DISPLAY_ITEM = getField(Display.ItemDisplay.class, "DATA_ITEM_STACK_ID");
            ITEM_DISPLAY_CONTEXT = getField(Display.ItemDisplay.class, "DATA_ITEM_DISPLAY_ID");

        } catch (Exception exception) {
            InertiaLogger.error("CRITICAL: Failed to access NMS MetadataAccessors for 1.21.4 (v1_21_r8)!", exception);
        }
    }

    private MetadataAccessors() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> getField(Class<?> targetClass, String fieldName) throws Exception {
        if (targetClass == null || fieldName == null) {
            throw new IllegalArgumentException("Target class and field name must not be null");
        }
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (value == null) {
            throw new IllegalStateException("Field " + fieldName + " in " + targetClass.getName() + " is null");
        }
        return (EntityDataAccessor<T>) value;
    }
}