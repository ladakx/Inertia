/* Original project path: inertia-core/src/main/java/com/ladakx/inertia/core/InertiaCore.java */

package com.ladakx.inertia.core;

import com.ladakx.inertia.api.Inertia;
import com.ladakx.inertia.core.engine.PhysicsEngine;
import com.ladakx.inertia.core.object.InertiaObjectManager;
import com.ladakx.inertia.core.visual.BodyVisualManager;
import org.bukkit.plugin.java.JavaPlugin;

public class InertiaCore {

    private final JavaPlugin plugin;
    private final PhysicsEngine physicsEngine;
    private final BodyVisualManager bodyVisualManager;
    private final InertiaObjectManager inertiaObjectManager;
    private final InertiaApiImpl api; // The concrete implementation

    public InertiaCore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.physicsEngine = new PhysicsEngine(plugin);
        this.bodyVisualManager = new BodyVisualManager(this);
        this.inertiaObjectManager = new InertiaObjectManager(this);
        this.api = new InertiaApiImpl(this);
    }

    public void load() {
        this.physicsEngine.start();
        this.bodyVisualManager.startSyncTask();
    }

    public void unload() {
        this.physicsEngine.stop();
    }

    /**
     * @return The public API instance for registration.
     */
    public Inertia getApi() {
        return api;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public PhysicsEngine getPhysicsEngine() {
        return physicsEngine;
    }

    public BodyVisualManager getBodyVisualManager() {
        return bodyVisualManager;
    }

    public InertiaObjectManager getInertiaObjectManager() {
        return inertiaObjectManager;
    }
}