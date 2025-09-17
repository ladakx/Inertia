package com.ladakx.inertia;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.core.InertiaAPIImpl;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class InertiaSpigotPlugin extends JavaPlugin {

    private PhysicsManager physicsManager;

    @Override
    public void onEnable() {
        // Initialize the logger first
        InertiaPluginLogger.initialize(getLogger());

        // Initialize the physics manager
        this.physicsManager = new PhysicsManager(this);
        if (!physicsManager.initialize()) {
            // Initialization failed, disable the plugin
            InertiaPluginLogger.severe("Failed to initialize PhysicsManager. Disabling Inertia plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize and register the API
        InertiaAPI.setInstance(new InertiaAPIImpl(this.physicsManager));

        InertiaPluginLogger.info("Inertia plugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Shutdown the physics manager to release native resources
        if (this.physicsManager != null) {
            physicsManager.shutdown();
        }

        InertiaPluginLogger.info("Inertia plugin has been disabled.");
    }
}
