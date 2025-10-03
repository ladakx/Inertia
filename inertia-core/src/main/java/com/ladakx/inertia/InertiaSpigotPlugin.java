package com.ladakx.inertia;


import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class InertiaSpigotPlugin extends JavaPlugin {

    private PhysicsManager physicsManager;

    @Override
    public void onEnable() {
        InertiaPluginLogger.initialize(getLogger());

        this.physicsManager = new PhysicsManager(this);
        if (!physicsManager.initialize()) {
            InertiaPluginLogger.severe("Failed to initialize PhysicsManager. Disabling Inertia plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        InertiaPluginLogger.info("Inertia plugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (this.physicsManager != null) {
            physicsManager.shutdown();
        }

        InertiaPluginLogger.info("Inertia plugin has been disabled.");
    }
}

