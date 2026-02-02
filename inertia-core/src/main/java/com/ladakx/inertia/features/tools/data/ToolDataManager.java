package com.ladakx.inertia.features.tools.data;

import com.ladakx.inertia.common.pdc.InertiaPDCUtils;
import com.ladakx.inertia.core.InertiaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ToolDataManager {
    private static final String KEY_TOOL_ID = "inertia_tool_id";
    private static final String KEY_BODY_ID = "inertia_body_id";
    private static final String KEY_SHAPE_TYPE = "inertia_shape_type";
    private static final String KEY_SHAPE_PARAMS = "inertia_shape_params";
    private static final String KEY_TNT_FORCE = "inertia_tnt_force";

    private final InertiaPlugin plugin;

    public ToolDataManager(InertiaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setToolId(@NotNull ItemStack item, @NotNull String toolId) {
        InertiaPDCUtils.setString(plugin, item, KEY_TOOL_ID, toolId);
    }

    public @Nullable String getToolId(@Nullable ItemStack item) {
        return InertiaPDCUtils.getString(plugin, item, KEY_TOOL_ID);
    }

    public boolean isTool(@Nullable ItemStack item, @NotNull String expectedId) {
        String id = getToolId(item);
        return id != null && id.equals(expectedId);
    }

    public void setBodyId(@NotNull ItemStack item, @NotNull String bodyId) {
        InertiaPDCUtils.setString(plugin, item, KEY_BODY_ID, bodyId);
    }

    public @Nullable String getBodyId(@Nullable ItemStack item) {
        return InertiaPDCUtils.getString(plugin, item, KEY_BODY_ID);
    }

    public void setShapeData(@NotNull ItemStack item, @NotNull String type, double[] params) {
        InertiaPDCUtils.setString(plugin, item, KEY_SHAPE_TYPE, type);
        String paramsStr = String.join(";", Arrays.stream(params).mapToObj(String::valueOf).toArray(String[]::new));
        InertiaPDCUtils.setString(plugin, item, KEY_SHAPE_PARAMS, paramsStr);
    }

    public @Nullable String getShapeType(@Nullable ItemStack item) {
        return InertiaPDCUtils.getString(plugin, item, KEY_SHAPE_TYPE);
    }

    public double[] getShapeParams(@Nullable ItemStack item) {
        String raw = InertiaPDCUtils.getString(plugin, item, KEY_SHAPE_PARAMS);
        if (raw == null || raw.isEmpty()) return new double[0];
        try {
            return Arrays.stream(raw.split(";")).mapToDouble(Double::parseDouble).toArray();
        } catch (NumberFormatException e) {
            return new double[0];
        }
    }

    public void setTntForce(@NotNull ItemStack item, float force) {
        InertiaPDCUtils.setString(plugin, item, KEY_TNT_FORCE, String.valueOf(force));
    }

    public float getTntForce(@Nullable ItemStack item, float def) {
        String raw = InertiaPDCUtils.getString(plugin, item, KEY_TNT_FORCE);
        if (raw == null) return def;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}