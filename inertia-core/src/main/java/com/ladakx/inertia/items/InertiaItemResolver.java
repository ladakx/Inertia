package com.ladakx.inertia.items;

import com.ladakx.inertia.render.ItemModelResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class InertiaItemResolver implements ItemModelResolver {

    private final ItemManager itemManager;

    // Inject ItemManager
    public InertiaItemResolver(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public @Nullable ItemStack resolve(String key) {
        return itemManager.getItem(key);
    }

    @Override
    public boolean canResolve(String itemModelKey) {
        return itemManager.hasItem(itemModelKey);
    }
}