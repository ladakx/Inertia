package com.ladakx.inertia;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.core.InertiaAPIImpl;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import com.ladakx.inertia.core.synchronization.SyncTask;
import com.ladakx.inertia.core.visualization.BodyVisualizer;
import com.ladakx.inertia.plugin.commands.SpawnCubeCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class InertiaSpigotPlugin extends JavaPlugin {

    private PhysicsManager physicsManager;
    private BodyVisualizer bodyVisualizer;
    private BukkitTask syncTask;

    @Override
    public void onEnable() {
        // Initialize the logger first
        InertiaPluginLogger.initialize(getLogger());

        // Initialize managers
        this.physicsManager = new PhysicsManager(this);
        this.bodyVisualizer = new BodyVisualizer();

        if (!physicsManager.initialize()) {
            // Initialization failed, disable the plugin
            InertiaPluginLogger.severe("Failed to initialize PhysicsManager. Disabling Inertia plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize and register the API
        InertiaAPI.setInstance(new InertiaAPIImpl(this.physicsManager));

        // Start the synchronization task
        this.syncTask = new SyncTask(this.physicsManager, this.bodyVisualizer).runTaskTimer(this, 0L, 1L);

        // Register commands
        Objects.requireNonNull(getCommand("inertiaspawn")).setExecutor(new SpawnCubeCommand(this));


        InertiaPluginLogger.info("Inertia plugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Stop the sync task
        if (this.syncTask != null && !this.syncTask.isCancelled()) {
            this.syncTask.cancel();
        }

        // Shutdown the physics manager to release native resources
        if (this.physicsManager != null) {
            physicsManager.shutdown();
        }

        // Clear the visualizer
        if (this.bodyVisualizer != null) {
            this.bodyVisualizer.clear();
        }

        InertiaPluginLogger.info("Inertia plugin has been disabled.");
    }

    public BodyVisualizer getBodyVisualizer() {
        return bodyVisualizer;
    }
}

