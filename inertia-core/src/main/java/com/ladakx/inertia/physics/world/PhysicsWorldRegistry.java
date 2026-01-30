package com.ladakx.inertia.physics.world;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.engine.PhysicsEngine;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsWorldRegistry {

    private final InertiaPlugin plugin;
    private final ConfigurationService configurationService;
    private final PhysicsEngine physicsEngine;

    private final Map<UUID, PhysicsWorld> spaces = new ConcurrentHashMap<>();

    // Внедрение зависимостей
    public PhysicsWorldRegistry(InertiaPlugin plugin, ConfigurationService configurationService, PhysicsEngine physicsEngine) {
        this.plugin = plugin;
        this.configurationService = configurationService;
        this.physicsEngine = physicsEngine;

        InertiaLogger.info("Loading existing worlds into Inertia Jolt...");
        for (World world : Bukkit.getWorlds()) {
            createSpace(world);
        }
    }

    public PhysicsWorld getSpace(World world) {
        return spaces.computeIfAbsent(world.getUID(), k -> createSpaceInternal(world));
    }

    public PhysicsWorld getSpace(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) return null;
        return getSpace(world);
    }

    private PhysicsWorld createSpaceInternal(World world) {
        InertiaLogger.info("Creating physics space for world: " + world.getName());
        // Используем инстанс configManager
        WorldsConfig.WorldProfile settings = configurationService.getWorldsConfig().getWorldSettings(world.getName());

        return new PhysicsWorld(
                world,
                settings,
                physicsEngine.getJobSystem(),
                physicsEngine.getTempAllocator()
        );
    }

    public void createSpace(World world) {
        if (!spaces.containsKey(world.getUID())) {
            // Используем инстанс configManager
            if (configurationService.getWorldsConfig().getAllWorlds().containsKey(world.getName())) {
                spaces.put(world.getUID(), createSpaceInternal(world));
            } else {
                InertiaLogger.info("World " + world.getName() + " is not configured for Inertia Jolt. Skipping space creation.");
            }
        }
    }

    public void removeSpace(World world) {
        PhysicsWorld space = spaces.remove(world.getUID());
        if (space != null) {
            space.close();
        }
    }

    public void shutdown() {
        InertiaLogger.info("Shutting down SpaceManager...");
        for (PhysicsWorld space : spaces.values()) {
            space.close();
        }
        spaces.clear();
    }
}