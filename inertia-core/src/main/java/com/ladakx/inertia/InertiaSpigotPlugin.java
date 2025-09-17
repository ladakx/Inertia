package com.ladakx.inertia;

import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.core.InertiaAPIImpl;
import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.commands.SpawnCubeCommand;
import com.ladakx.inertia.core.physics.PhysicsManager;
import com.ladakx.inertia.core.synchronization.SyncTask;
import com.ladakx.inertia.core.visualization.BodyVisualizer;
import com.ladakx.inertia.core.world.ChunkManager;
import com.ladakx.inertia.core.world.listeners.ChunkListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class InertiaSpigotPlugin extends JavaPlugin {

    private PhysicsManager physicsManager;
    private BodyVisualizer bodyVisualizer;
    private ChunkManager chunkManager;
    private SyncTask syncTask;

    @Override
    public void onEnable() {
        InertiaPluginLogger.initialize(getLogger());

        this.physicsManager = new PhysicsManager(this);
        if (!physicsManager.initialize()) {
            InertiaPluginLogger.severe("Failed to initialize PhysicsManager. Disabling Inertia plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bodyVisualizer = new BodyVisualizer();
        this.chunkManager = new ChunkManager(physicsManager);

        InertiaAPI.setInstance(new InertiaAPIImpl(physicsManager));

        this.syncTask = new SyncTask(physicsManager, bodyVisualizer);
        this.syncTask.runTaskTimer(this, 0L, 1L);

        getServer().getPluginManager().registerEvents(new ChunkListener(chunkManager), this);
        getCommand("inertiaspawn").setExecutor(new SpawnCubeCommand(this, bodyVisualizer));

        InertiaPluginLogger.info("Inertia plugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (this.syncTask != null) {
            this.syncTask.cancel();
        }
        if (this.chunkManager != null) {
            this.chunkManager.shutdown();
        }
        if (this.physicsManager != null) {
            physicsManager.shutdown();
        }

        InertiaPluginLogger.info("Inertia plugin has been disabled.");
    }
}

