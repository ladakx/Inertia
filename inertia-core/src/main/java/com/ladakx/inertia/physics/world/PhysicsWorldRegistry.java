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

        // Initial load
        Bukkit.getScheduler().runTask(plugin, () -> {
            InertiaLogger.info("Loading existing worlds into Inertia Jolt...");
            for (World world : Bukkit.getWorlds()) {
                createSpace(world);
            }
        });
    }

    public PhysicsWorld getSpace(World world) {
        return spaces.get(world.getUID());
    }

    public PhysicsWorld getSpace(UUID worldId) {
        return spaces.get(worldId);
    }

    private PhysicsWorld createSpaceInternal(World world) {
        InertiaLogger.info("Creating physics space for world: " + world.getName());
        WorldsConfig.WorldProfile settings = configurationService.getWorldsConfig().getWorldSettings(world.getName());

        TerrainAdapter terrainAdapter = null;
        if (settings.simulation().enabled() && settings.simulation().type() == SimulationType.FLOOR_PLANE) {
            terrainAdapter = new FlatFloorAdapter(settings.simulation().floorPlane());
        }

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
                try {
                    PhysicsWorld space = createSpaceInternal(world);
                    spaces.put(world.getUID(), space);
                } catch (Exception e) {
                    InertiaLogger.error("Failed to initialize physics world: " + world.getName(), e);
                }
            } else {
                // Not necessarily an error, maybe user didn't configure this world
                // InertiaLogger.debug("World " + world.getName() + " is not configured for Inertia Jolt.");
            }
        }
    }

    public void removeSpace(World world) {
        PhysicsWorld space = spaces.remove(world.getUID());
        if (space != null) {
            space.close();
        }
    }

    /**
     * Reloads all physics worlds based on the new configuration.
     * Warning: This destroys all current physics bodies!
     */
    public void reload() {
        InertiaLogger.info("Reloading Physics World Registry...");

        // 1. Close all existing worlds
        for (PhysicsWorld space : spaces.values()) {
            try {
                space.close();
            } catch (Exception e) {
                InertiaLogger.error("Error closing world during reload: " + space.getBukkitWorld().getName(), e);
            }
        }
        spaces.clear();

        // 2. Re-initialize worlds that are currently loaded in Bukkit
        for (World world : Bukkit.getWorlds()) {
            createSpace(world);
        }

        InertiaLogger.info("Physics worlds reloaded.");
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