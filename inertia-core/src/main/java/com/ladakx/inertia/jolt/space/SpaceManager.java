package com.ladakx.inertia.jolt.space;

import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.files.config.InertiaConfig;
import com.ladakx.inertia.jolt.JoltManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpaceManager {

    private final JoltManager joltManager;
    private final InertiaPlugin plugin;

    // Карта: UUID світу Bukkit -> Фізичний простір
    private final Map<UUID, MinecraftSpace> spaces = new ConcurrentHashMap<>();

    public SpaceManager(InertiaPlugin plugin) {
        this.joltManager = JoltManager.getInstance();
        this.plugin = plugin;

        // 1. Підгружаємо світи, які вже є на сервері при запуску плагіна
        InertiaLogger.info("Loading existing worlds into Inertia Jolt...");
        for (World world : Bukkit.getWorlds()) {
            createSpace(world);
        }
    }

    /**
     * Отримати фізичний простір за світом Bukkit.
     * Створить новий, якщо його ще немає.
     */
    public MinecraftSpace getSpace(World world) {
        return spaces.computeIfAbsent(world.getUID(), k -> createSpaceInternal(world));
    }

    public MinecraftSpace getSpace(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) return null;
        return getSpace(world);
    }

    private MinecraftSpace createSpaceInternal(World world) {
        InertiaLogger.info("Creating physics space for world: " + world.getName());
        InertiaConfig.PhysicsSettings.WorldSettings settings =
                plugin.getConfigManager().getInertiaConfig().PHYSICS.getWorld(world.getName());
        return new MinecraftSpace(
                world,
                settings,
                joltManager.getJobSystem(),
                joltManager.getTempAllocator()
        );
    }

    public void createSpace(World world) {
        if (!spaces.containsKey(world.getUID())) {
            spaces.put(world.getUID(), createSpaceInternal(world));
        }
    }

    public void removeSpace(World world) {
        MinecraftSpace space = spaces.remove(world.getUID());
        if (space != null) {
            space.close();
        }
    }

    public void shutdown() {
        InertiaLogger.info("Shutting down SpaceManager...");
        for (MinecraftSpace space : spaces.values()) {
            space.close();
        }
        spaces.clear();

        // Вимикаємо і сам Jolt
        joltManager.shutdown();
    }
}