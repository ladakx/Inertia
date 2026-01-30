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

    private final InertiaPlugin plugin;
    private final ConfigManager configManager;
    private final JoltManager joltManager;

    private final Map<UUID, MinecraftSpace> spaces = new ConcurrentHashMap<>();

    // Внедрение зависимостей
    public SpaceManager(InertiaPlugin plugin, ConfigManager configManager, JoltManager joltManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.joltManager = joltManager;

        InertiaLogger.info("Loading existing worlds into Inertia Jolt...");
        for (World world : Bukkit.getWorlds()) {
            createSpace(world);
        }
    }

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
        // Используем инстанс configManager
        WorldsConfig.WorldProfile settings = configManager.getWorldsConfig().getWorldSettings(world.getName());

        return new MinecraftSpace(
                world,
                settings,
                joltManager.getJobSystem(),
                joltManager.getTempAllocator()
        );
    }

    public void createSpace(World world) {
        if (!spaces.containsKey(world.getUID())) {
            // Используем инстанс configManager
            if (configManager.getWorldsConfig().getAllWorlds().containsKey(world.getName())) {
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
}