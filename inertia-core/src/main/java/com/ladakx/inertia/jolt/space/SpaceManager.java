package com.ladakx.inertia.jolt.space;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.config.ConfigManager;
import com.ladakx.inertia.config.WorldsConfig;
import com.ladakx.inertia.jolt.JoltManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpaceManager {

    private static SpaceManager instance;

    private final InertiaPlugin plugin;

    // Карта: UUID світу Bukkit -> Фізичний простір
    private final Map<UUID, MinecraftSpace> spaces = new ConcurrentHashMap<>();

    private SpaceManager(InertiaPlugin plugin) {
        this.plugin = plugin;

        // 1. Підгружаємо світи, які вже є на сервері при запуску плагіна
        InertiaLogger.info("Loading existing worlds into Inertia Jolt...");
        for (World world : Bukkit.getWorlds()) {
            createSpace(world);
        }
    }

    public static void init(InertiaPlugin plugin) {
        if (instance == null) {
            instance = new SpaceManager(plugin);
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
        WorldsConfig.WorldProfile settings = ConfigManager.getInstance().getWorldsConfig().getWorldSettings(world.getName());
        return new MinecraftSpace(
                world,
                settings,
                JoltManager.getInstance().getJobSystem(),
                JoltManager.getInstance().getTempAllocator()
        );
    }

    public void createSpace(World world) {
        if (!spaces.containsKey(world.getUID())) {
            if (ConfigManager.getInstance().getWorldsConfig().getAllWorlds().containsKey(world.getName())) {
                spaces.put(world.getUID(), createSpaceInternal(world));
            } else {
                InertiaLogger.info("World " + world.getName() + " is not configured for Inertia Jolt. Skipping space creation.");
            }
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
    }

    public static SpaceManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SpaceManager not initialized! Call init() first.");
        }
        return instance;
    }
}