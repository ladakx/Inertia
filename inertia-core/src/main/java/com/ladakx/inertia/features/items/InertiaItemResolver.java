package com.ladakx.inertia.features.items;

import com.ladakx.inertia.rendering.ItemModelResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class InertiaItemResolver implements ItemModelResolver {

    private final ItemRegistry itemRegistry;

    // Inject ItemRegistry
    public InertiaItemResolver(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    @Override
    public @Nullable ItemStack resolve(String key) {
        return itemRegistry.getItem(key);
    }

    @Override
    public boolean canResolve(String itemModelKey) {
        return itemRegistry.hasItem(itemModelKey);
    }
}