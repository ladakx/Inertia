package com.ladakx.inertia.rendering;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Абстракція над джерелом item-моделей (items.yml / ItemRegistry).
 */
public interface ItemModelResolver {

    /**
     * @param itemModelKey ключ з render.yml → items.yml
     * @return побудований ItemStack або null, якщо не знайдено
     */
    @Nullable
    ItemStack resolve(String itemModelKey);

    boolean canResolve(String itemModelKey);
}