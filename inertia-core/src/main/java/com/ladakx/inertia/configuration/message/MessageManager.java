package com.ladakx.inertia.configuration.message;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.utils.MessageUtils;
import com.ladakx.inertia.common.utils.StringUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MessageManager {

    // Cache: Зберігаємо вже готові Component, щоб не парсити їх щоразу при відправці
    private final Map<MessageKey, List<Component>> messageCache = new EnumMap<>(MessageKey.class);

    public MessageManager() {
    }

    /**
     * Reloads messages from the provided config into cache.
     */
    public void reload(FileConfiguration cfg) {
        messageCache.clear();

        // 1. Отримуємо префікс один раз
        String prefix = cfg.getString(MessageKey.PREFIX.getPath(), "<gray>[<red>Inertia<gray>] ");

        // 2. Проходимо по всіх ключах і кешуємо повідомлення
        int loadedCount = 0;
        for (MessageKey key : MessageKey.values()) {
            String path = key.getPath();

            if (cfg.contains(path)) {
                List<String> rawLines;

                // Підтримка і String, і List<String> у конфізі
                if (cfg.isList(path)) {
                    rawLines = cfg.getStringList(path);
                } else {
                    rawLines = List.of(cfg.getString(path, ""));
                }

                // Парсимо рядки в компоненти
                List<Component> components = new ArrayList<>();
                for (String line : rawLines) {
                    if (line.isEmpty()) continue;
                    components.add(MessageUtils.parse(line, prefix));
                }

                // Зберігаємо в кеш, тільки якщо є що зберігати
                if (!components.isEmpty()) {
                    messageCache.put(key, components);
                    loadedCount++;
                }
            }
        }

        InertiaLogger.info("Loaded " + loadedCount + " messages.");
    }

    // --- Access Methods ---
    /**
     * Gets the raw components list for a message key (useful for Item Meta lore).
     * @param key The message key
     * @return List of components, or empty list if not found.
     */
    public List<Component> get(MessageKey key) {
        return messageCache.getOrDefault(key, new ArrayList<>());
    }

    /**
     * Helper to get a single component (e.g. for Display Name).
     */
    public Component getSingle(MessageKey key) {
        List<Component> list = get(key);
        return list.isEmpty() ? Component.empty() : list.get(0);
    }

    // --- Sending Methods ---
    public void send(CommandSender sender, MessageKey key, String... replacements) {
        if (sender == null) return;

        if (sender instanceof Audience) {
            Audience audience = (Audience) sender;
            send(audience, key, replacements);
            return;
        }

        List<Component> lines = messageCache.get(key);
        if (lines == null || lines.isEmpty()) return;

        for (Component line : lines) {
            Component component = (replacements.length > 0) ? StringUtils.replace(line, replacements) : line;
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
        }
    }

    public void send(Audience audience, MessageKey key, String... replacements) {
        if (audience == null) return;
        List<Component> lines = messageCache.get(key);

        // Якщо повідомлення немає в кеші (немає в конфізі або пусте) - нічого не робимо
        if (lines == null || lines.isEmpty()) return;

        for (Component line : lines) {
            if (replacements.length > 0) {
                audience.sendMessage(StringUtils.replace(line, replacements));
            } else {
                audience.sendMessage(line);
            }
        }
    }
}
