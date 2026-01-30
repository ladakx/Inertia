package com.ladakx.inertia.configuration.files;

import com.ladakx.inertia.core.InertiaPlugin;

public class MessagesFile extends CustomConfiguration {

    /** Create file */
    public MessagesFile(InertiaPlugin plugin) {
        super(plugin, "lang/lang_"+plugin.getConfig().getString("general.lang", "en") +".yml");
    }
}
