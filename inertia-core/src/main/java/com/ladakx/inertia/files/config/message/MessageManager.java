package com.ladakx.inertia.files.config.message;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.utils.MessageUtils;
import com.ladakx.inertia.utils.StringUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

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
        String prefix = cfg.getString("Prefix", "<gray>[<red>Inertia<gray>] ");

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

    // --- Sending Methods ---

    public void send(CommandSender sender, MessageKey key, String... replacements) {
        send(sender, key, replacements);
    }

    public void send(Player player, MessageKey key, String... replacements) {
        send(player, key, replacements);
    }

    public void send(Audience audience, MessageKey key, String... replacements) {
        List<Component> lines = messageCache.get(key);

        // Якщо повідомлення немає в кеші (немає в конфізі або пусте) - нічого не робимо
        if (lines == null || lines.isEmpty()) return;

        for (Component line : lines) {
            if (replacements.length > 0) {
                // Використовуємо твій StringUtils для заміни плейсхолдерів у вже готовому компоненті
                audience.sendMessage(StringUtils.replace(line, replacements));
            } else {
                audience.sendMessage(line);
            }
        }
    }
}