package com.ladakx.inertia.utils;

import com.ladakx.inertia.InertiaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for messages. Used to retrieve messages from the config.
 */
public class MessageUtils {
    /**
     * Retrieves a String message from a config and converts it to a Component.
     * @param path The path of the message in the config.
     * @return true if the message is empty, false otherwise.
     */
    public static boolean isEmpty(String path) {
        return InertiaPlugin.getMessages().getConfig().getStringList(path).isEmpty();
    }

    /**
     * Retrieves a List<String> message from a config and converts it to a List<Component>.
     * @param string The key of the message in the config.
     * @return The converted List<Component>.
     */
    public static List<Component> getMessage(String string) {
        String prefix = InertiaPlugin.getMessages().getConfig().getString("Prefix");
        List<String> text = InertiaPlugin.getMessages().getConfig().getStringList(string);
        List<Component> result = new ArrayList<>();

        for (String s : text) {
            result.add(MiniMessage.miniMessage().deserialize(StringUtils.colorAdventure(s.replace("{prefix}", prefix))));
        }

        return result;
    }
}
