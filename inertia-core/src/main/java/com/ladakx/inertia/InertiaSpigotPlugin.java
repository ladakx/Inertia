package com.ladakx.inertia;

import com.ladakx.inertia.core.InertiaPluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

public final class InertiaSpigotPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InertiaPluginLogger.initialize(getLogger());
        InertiaPluginLogger.info("Inertia plugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        InertiaPluginLogger.info("Inertia plugin has been disabled.");
    }
}

