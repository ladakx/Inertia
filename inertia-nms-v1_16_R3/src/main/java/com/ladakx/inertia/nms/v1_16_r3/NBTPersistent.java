package com.ladakx.inertia.nms.v1_16_r3;

import com.ladakx.inertia.utils.StringUtils;
import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NBTPersistent extends com.ladakx.inertia.nms.nbt.NBTPersistent {


    @Override
    public void copyTagsFromTo(ItemStack fromItem, ItemStack toItem, String path) {
        net.minecraft.server.v1_16_R3.ItemStack nms = getNMSStack(toItem);
        NBTTagCompound from = getNMSStack(fromItem).getTag();
        NBTTagCompound to = nms.getTag();

        if (path == null) {
            nms.setTag(from.clone());
            toItem.setItemMeta(CraftItemStack.asBukkitCopy(nms).getItemMeta());
            return;
        }

        to.set(path, from.getCompound(path).clone());
        toItem.setItemMeta(CraftItemStack.asBukkitCopy(nms).getItemMeta());
    }

    @Override
    public net.minecraft.server.v1_16_R3.ItemStack getNMSStack(ItemStack bukkitStack) {
        return CraftItemStack.asNMSCopy(bukkitStack);
    }

    @Override
    public ItemStack getBukkitStack(Object nmsStack) {
        return CraftItemStack.asBukkitCopy((net.minecraft.server.v1_16_R3.ItemStack) nmsStack);
    }

    @Override
    public String getNBTDebug(ItemStack bukkitStack) {
        NBTTagCompound nbt = getNMSStack(bukkitStack).getTag();
        if (nbt == null)
            return "null";

        return visit(nbt, 0, 0).toString();
    }

    private static final String BRACE_COLORS = "f780"; // grayscale colors
    private static final String VALUE_COLORS = "6abcdef"; // bright colors

    private StringBuilder visit(NBTTagCompound nbt, int indents, int colorOffset) {
        String braceColor = "&" + BRACE_COLORS.charAt(indents % BRACE_COLORS.length());
        StringBuilder builder = new StringBuilder(braceColor).append('{');

        List<String> keys = new ArrayList<>(nbt.getKeys());
        Collections.sort(keys);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            NBTBase value = Objects.requireNonNull(nbt.get(key), "This is impossible");

            if (i != 0)
                builder.append('\n');

            builder.append(StringUtils.repeat("  ", indents));
            String color = "&" + VALUE_COLORS.charAt((i + colorOffset) % VALUE_COLORS.length());
            builder.append(color).append(key).append("&f&l: ").append(color);

            if (value instanceof NBTTagCompound)
                builder.append(visit((NBTTagCompound) value, indents + 1, colorOffset + i));
            else
                builder.append(value);
        }

        return builder.append(braceColor).append("}\n");
    }
}