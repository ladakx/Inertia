package com.ladakx.inertia;

import com.ladakx.inertia.core.InertiaPluginLogger;
import com.ladakx.inertia.core.physics.PhysicsManager;
import com.ladakx.inertia.core.physics.threading.EventGameQueue;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class InertiaSpigotPlugin extends JavaPlugin {

    private InertiaPluginLogger pluginLogger;
    private PhysicsManager physicsManager;
    private EventGameQueue eventGameQueue;

    @Override
    public void onEnable() {
        this.pluginLogger = new InertiaPluginLogger(this.getLogger());

        // --- Попередження про /reload ---
        pluginLogger.warn("-----------------------------------------------------");
        pluginLogger.warn("Inertia has been enabled.");
        pluginLogger.warn("This plugin does NOT support server reloads (/reload).");
        pluginLogger.warn("Please restart the server completely to apply changes.");
        pluginLogger.warn("-----------------------------------------------------");

        try {
            this.eventGameQueue = new EventGameQueue(pluginLogger);
            this.physicsManager = new PhysicsManager(this, pluginLogger, eventGameQueue);

            // ВИПРАВЛЕНО: Передаємо 'this' (JavaPlugin) для доступу до ресурсів JAR
            this.physicsManager.initialize();

            // Запускаємо Bukkit-таск для обробки подій з фізичного потоку
            startEventGameLoop();

            pluginLogger.info("Inertia v" + this.getDescription().getVersion() + " enabled successfully.");

        } catch (Throwable t) {
            pluginLogger.severe("CRITICAL: Failed to initialize Inertia PhysicsManager.", t);
            pluginLogger.severe("Inertia plugin will be disabled to prevent server instability.");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.physicsManager != null) {
            try {
                this.physicsManager.shutdown();
                pluginLogger.info("PhysicsManager shut down successfully.");
            } catch (Throwable t) {
                pluginLogger.severe("An error occurred during PhysicsManager shutdown.", t);
            }
        } else {
            pluginLogger.info("Inertia is disabling (was not fully enabled).");
        }
        this.physicsManager = null;
        this.pluginLogger = null;
    }

    /**
     * Запускає синхронний таск (1 раз на тік) для обробки
     * подій, що надійшли з фізичного потоку (напр., зіткнення).
     */
    private void startEventGameLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled() || eventGameQueue == null) {
                    this.cancel();
                    return;
                }
                // Виконуємо всі завдання з черги в основному потоці
                eventGameQueue.processAll();
            }
        }.runTaskTimer(this, 1L, 1L); // Почати через 1 тік, повторювати кожен тік
    }
}