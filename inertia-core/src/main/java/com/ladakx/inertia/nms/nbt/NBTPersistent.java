package com.ladakx.inertia.nms.nbt;

import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Due to class loading issues, this logic (that was originally implemented in
 * {@link NBTCompatibility}) is now implemented here. This way, on 1.12.2,
 * {@link PersistentDataType} is not loaded.
 */
public abstract class NBTPersistent implements NBTCompatibility {

    public boolean hasString(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitItem.getItemMeta()).has(getKey(plugin, key), PersistentDataType.STRING);
    }

    public boolean hasString(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitEntity).has(getKey(plugin, key), PersistentDataType.STRING);
    }

    public String getString(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, String def) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.STRING, def);
    }

    public String getString(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, String def) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);

        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.STRING, def);
    }

    public void setString(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, String value) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.set(getKey(plugin, key), PersistentDataType.STRING, value);
        bukkitItem.setItemMeta(meta);
    }

    public void setString(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, String value) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.set(getKey(plugin, key), PersistentDataType.STRING, value);
    }

    public boolean hasInt(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitItem.getItemMeta()).has(getKey(plugin, key), PersistentDataType.INTEGER);
    }

    public boolean hasInt(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitEntity).has(getKey(plugin, key), PersistentDataType.INTEGER);
    }

    public int getInt(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, int def) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.INTEGER, def);
    }

    public int getInt(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, int def) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.INTEGER, def);
    }

    public void setInt(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, int value) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.set(getKey(plugin, key), PersistentDataType.INTEGER, value);
        bukkitItem.setItemMeta(meta);
    }

    public void setInt(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, int value) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.set(getKey(plugin, key), PersistentDataType.INTEGER, value);
    }

    public boolean hasDouble(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitItem.getItemMeta()).has(getKey(plugin, key), PersistentDataType.DOUBLE);
    }

    public boolean hasDouble(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitEntity).has(getKey(plugin, key), PersistentDataType.DOUBLE);
    }

    public double getDouble(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, double def) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.DOUBLE, def);
    }

    public double getDouble(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, double def) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        return nbt.getOrDefault(getKey(plugin, key), PersistentDataType.DOUBLE, def);
    }

    public void setDouble(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, double value) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.set(getKey(plugin, key), PersistentDataType.DOUBLE, value);
        bukkitItem.setItemMeta(meta);
    }

    public void setDouble(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, double value) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.set(getKey(plugin, key), PersistentDataType.DOUBLE, value);
    }

    public boolean hasArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitItem.getItemMeta()).has(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY);
    }

    public boolean hasArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitEntity).has(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY);
    }

    public int[] getArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, int[] def) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        return nbt.has(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY) ? nbt.get(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY) : def;
    }

    public int[] getArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, int[] def) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        return nbt.has(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY) ? nbt.get(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY) : def;
    }

    public void setArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, int[] value) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.set(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY, value);
        bukkitItem.setItemMeta(meta);
    }

    public void setArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, int[] value) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.set(getKey(plugin, key), PersistentDataType.INTEGER_ARRAY, value);
    }

    public boolean hasStringArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitItem.getItemMeta()).has(getKey(plugin, key), StringPersistentType.INSTANCE);
    }

    public boolean hasStringArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        return getCompound(bukkitEntity).has(getKey(plugin, key), StringPersistentType.INSTANCE);
    }

    public String[] getStringArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, String[] def) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        return nbt.has(getKey(plugin, key), StringPersistentType.INSTANCE) ? nbt.get(getKey(plugin, key), StringPersistentType.INSTANCE) : def;
    }

    public String[] getStringArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, String[] def) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        return nbt.has(getKey(plugin, key), StringPersistentType.INSTANCE) ? nbt.get(getKey(plugin, key), StringPersistentType.INSTANCE) : def;
    }

    public void setStringArray(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key, String[] value) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.set(getKey(plugin, key), StringPersistentType.INSTANCE, value);
        bukkitItem.setItemMeta(meta);
    }

    public void setStringArray(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key, String[] value) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.set(getKey(plugin, key), StringPersistentType.INSTANCE, value);
    }

    public void remove(@NotNull ItemStack bukkitItem, @NotNull String plugin, @NotNull String key) {
        ItemMeta meta = bukkitItem.getItemMeta();
        PersistentDataContainer nbt = getCompound(meta);

        nbt.remove(getKey(plugin, key));
        bukkitItem.setItemMeta(meta);
    }

    public void remove(@NotNull Entity bukkitEntity, @NotNull String plugin, @NotNull String key) {
        PersistentDataContainer nbt = getCompound(bukkitEntity);
        nbt.remove(getKey(plugin, key));
    }

    private PersistentDataContainer getCompound(@NotNull ItemMeta meta) {
        return meta.getPersistentDataContainer();
    }

    private PersistentDataContainer getCompound(@NotNull Entity entity) {
        return entity.getPersistentDataContainer();
    }
}