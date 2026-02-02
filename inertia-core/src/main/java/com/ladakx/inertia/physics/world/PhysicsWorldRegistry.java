package com.ladakx.inertia.physics.world;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.engine.PhysicsEngine;
import com.ladakx.inertia.physics.world.terrain.SimulationType;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.impl.FlatFloorAdapter;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicsWorldRegistry {
    private final InertiaPlugin plugin;
    private final ConfigurationService configurationService;
    private final PhysicsEngine physicsEngine;
    private final Map<UUID, PhysicsWorld> spaces = new ConcurrentHashMap<>();

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
        WorldsConfig.WorldProfile settings = configurationService.getWorldsConfig().getWorldSettings(world.getName());

        TerrainAdapter terrainAdapter = null;

        // Проверяем тип симуляции и наличие настроек пола
        if (settings.simulation().type() == SimulationType.FLOOR_PLANE) {
            terrainAdapter = new FlatFloorAdapter(settings.simulation().floorPlane());
        }

        // Также можно использовать settings.tempAllocatorSize() при создании PhysicsWorld, если передать его в PhysicsEngine
        // Но пока используем глобальный аллокатор из PhysicsEngine

        return new PhysicsWorld(
                world,
                settings,
                physicsEngine.getJobSystem(),
                physicsEngine.getTempAllocator(),
                terrainAdapter
        );
    }

    public void createSpace(World world) {
        if (!spaces.containsKey(world.getUID())) {
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

    public Collection<PhysicsWorld> getAllSpaces() {
        return Collections.unmodifiableCollection(spaces.values());
    }
}