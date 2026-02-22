package com.ladakx.inertia.features.items;

import com.ladakx.inertia.common.serializers.ItemSerializer;
import com.ladakx.inertia.rendering.ItemModelResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
        ParsedKey parsed = ParsedKey.parse(key);
        ItemStack item = itemRegistry.getItem(parsed.baseKey);
        if (parsed.skin == null) {
            return item;
        }
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            String skin = parsed.skin;
            Player online = skin != null && skin.length() <= 16 ? Bukkit.getPlayerExact(skin) : null;
            if (online != null) {
                skullMeta.setOwningPlayer(online);
            } else {
                ItemSerializer.applySkullTexture(skullMeta, skin);
            }
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    @Override
    public boolean canResolve(String itemModelKey) {
        return itemRegistry.hasItem(ParsedKey.parse(itemModelKey).baseKey);
    }

    private static final class ParsedKey {
        private final String baseKey;
        private final String skin;

        private ParsedKey(String baseKey, String skin) {
            this.baseKey = baseKey;
            this.skin = skin;
        }

        private static ParsedKey parse(String raw) {
            if (raw == null) {
                return new ParsedKey("", null);
            }
            int at = raw.indexOf('@');
            if (at < 0) {
                return new ParsedKey(raw, null);
            }

            String base = raw.substring(0, at);
            String params = raw.substring(at + 1);

            String skin = null;
            for (String part : params.split("@")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String k = part.substring(0, eq).trim();
                if (!k.equalsIgnoreCase("skin")) continue;
                String v = part.substring(eq + 1).trim();
                if (!v.isEmpty()) {
                    skin = v;
                }
            }

            return new ParsedKey(base, skin);
        }
    }
}
