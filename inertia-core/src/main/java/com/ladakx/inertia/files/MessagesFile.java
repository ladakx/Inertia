package com.ladakx.inertia.files;

import com.ladakx.inertia.InertiaPlugin;

public class MessagesFile extends CustomConfiguration {

    /** Create file */
    public MessagesFile(InertiaPlugin plugin) {
        super(plugin, plugin.getConfig().getString("general.lang", "en") +".yml");
    }
}
