package com.ladakx.inertia.items;

import com.ladakx.inertia.render.ItemModelResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Реалізація резолвера, яка делегує пошук предметів до ItemManager.
 * Це дозволяє модулю рендерингу не залежати напряму від логіки завантаження файлів.
 */
public class InertiaItemResolver implements ItemModelResolver {

    @Override
    public @Nullable ItemStack resolve(String key) {
        return ItemManager.getInstance().getItem(key);
    }

    @Override
    public boolean canResolve(String itemModelKey) {
        return ItemManager.getInstance().hasItem(itemModelKey);
    }
}