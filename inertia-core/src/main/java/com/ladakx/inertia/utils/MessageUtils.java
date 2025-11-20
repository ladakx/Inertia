package com.ladakx.inertia.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Pure utility class for parsing strings into Components.
 * Stateless and independent of Plugin instance.
 */
public class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Converts a raw string into a Component, applying replacements and colors.
     * @param text The raw text from config
     * @param prefix The prefix to replace "{prefix}" with
     * @return The parsed Component
     */
    public static Component parse(String text, String prefix) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // 1. Replace prefix
        // 2. Apply legacy colors (if needed via your StringUtils)
        // 3. Parse MiniMessage
        String processed = text.replace("{prefix}", prefix != null ? prefix : "");

        // Припускаємо, що StringUtils.colorAdventure повертає String з legacy кодами, адаптованими для MM
        if (processed.contains("&") || processed.contains("§")) {
            processed = StringUtils.colorAdventure(processed);
        }

        return MINI_MESSAGE.deserialize(processed);
    }
}