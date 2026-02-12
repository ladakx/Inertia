package com.ladakx.inertia.nms.v1_21_r2.utils;

import com.ladakx.inertia.common.logging.InertiaLogger;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;

public class MetadataAccessors {
    public static EntityDataAccessor<Byte> ENTITY_FLAGS;

    // Display (Base)
    public static EntityDataAccessor<Integer> DISPLAY_INTERPOLATION_DURATION;
    public static EntityDataAccessor<Integer> DISPLAY_TELEPORT_DURATION;
    public static EntityDataAccessor<Vector3f> DISPLAY_TRANSLATION;
    public static EntityDataAccessor<Vector3f> DISPLAY_SCALE;
    public static EntityDataAccessor<Quaternionf> DISPLAY_LEFT_ROTATION;
    public static EntityDataAccessor<Quaternionf> DISPLAY_RIGHT_ROTATION;
    public static EntityDataAccessor<Byte> DISPLAY_BILLBOARD;
    public static EntityDataAccessor<Integer> DISPLAY_BRIGHTNESS;
    public static EntityDataAccessor<Float> DISPLAY_VIEW_RANGE;
    public static EntityDataAccessor<Float> DISPLAY_SHADOW_RADIUS;
    public static EntityDataAccessor<Float> DISPLAY_SHADOW_STRENGTH;

    // Block Display
    public static EntityDataAccessor<BlockState> BLOCK_DISPLAY_STATE;

    // Item Display
    public static EntityDataAccessor<ItemStack> ITEM_DISPLAY_ITEM;
    public static EntityDataAccessor<Byte> ITEM_DISPLAY_CONTEXT;

    static {
        try {
            // Entity
            // public static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID;
            ENTITY_FLAGS = getField(Entity.class, "DATA_SHARED_FLAGS_ID");

            // Display (Base)
            // private static final EntityDataAccessor<Integer> DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID;
            DISPLAY_INTERPOLATION_DURATION = getField(Display.class, "DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID");

            // public static final EntityDataAccessor<Integer> DATA_POS_ROT_INTERPOLATION_DURATION_ID;
            DISPLAY_TELEPORT_DURATION = getField(Display.class, "DATA_POS_ROT_INTERPOLATION_DURATION_ID");

            // private static final EntityDataAccessor<Vector3f> DATA_TRANSLATION_ID;
            DISPLAY_TRANSLATION = getField(Display.class, "DATA_TRANSLATION_ID");

            // private static final EntityDataAccessor<Vector3f> DATA_SCALE_ID;
            DISPLAY_SCALE = getField(Display.class, "DATA_SCALE_ID");

            // private static final EntityDataAccessor<Quaternionf> DATA_LEFT_ROTATION_ID;
            DISPLAY_LEFT_ROTATION = getField(Display.class, "DATA_LEFT_ROTATION_ID");

            // private static final EntityDataAccessor<Quaternionf> DATA_RIGHT_ROTATION_ID;
            DISPLAY_RIGHT_ROTATION = getField(Display.class, "DATA_RIGHT_ROTATION_ID");

            // private static final EntityDataAccessor<Byte> DATA_BILLBOARD_RENDER_CONSTRAINTS_ID;
            DISPLAY_BILLBOARD = getField(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");

            // private static final EntityDataAccessor<Integer> DATA_BRIGHTNESS_OVERRIDE_ID;
            DISPLAY_BRIGHTNESS = getField(Display.class, "DATA_BRIGHTNESS_OVERRIDE_ID");

            // private static final EntityDataAccessor<Float> DATA_VIEW_RANGE_ID;
            DISPLAY_VIEW_RANGE = getField(Display.class, "DATA_VIEW_RANGE_ID");

            // private static final EntityDataAccessor<Float> DATA_SHADOW_RADIUS_ID;
            DISPLAY_SHADOW_RADIUS = getField(Display.class, "DATA_SHADOW_RADIUS_ID");

            // private static final EntityDataAccessor<Float> DATA_SHADOW_STRENGTH_ID;
            DISPLAY_SHADOW_STRENGTH = getField(Display.class, "DATA_SHADOW_STRENGTH_ID");

            // Block Display
            // private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID;
            BLOCK_DISPLAY_STATE = getField(Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");

            // Item Display
            // private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK_ID;
            ITEM_DISPLAY_ITEM = getField(Display.ItemDisplay.class, "DATA_ITEM_STACK_ID");

            // private static final EntityDataAccessor<Byte> DATA_ITEM_DISPLAY_ID;
            ITEM_DISPLAY_CONTEXT = getField(Display.ItemDisplay.class, "DATA_ITEM_DISPLAY_ID");

        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize NMS Metadata Accessors via Reflection!", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> getField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (EntityDataAccessor<T>) field.get(null);
    }
}