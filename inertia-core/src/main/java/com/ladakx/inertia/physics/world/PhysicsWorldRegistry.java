package com.ladakx.inertia.physics.world;

import com.github.stephengold.joltjni.PhysicsSystem;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.dto.WorldsConfig;
import com.ladakx.inertia.physics.engine.JoltSystemFactory;
import com.ladakx.inertia.physics.engine.PhysicsEngine;
import com.ladakx.inertia.physics.world.terrain.SimulationType;
import com.ladakx.inertia.physics.world.terrain.TerrainAdapter;
import com.ladakx.inertia.physics.world.terrain.impl.FlatFloorAdapter;
import com.ladakx.inertia.physics.world.terrain.impl.GreedyMeshAdapter;
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
    private final JoltSystemFactory systemFactory;
    private final PhysicsMetricsService metricsService;
    private final Map<UUID, PhysicsWorld> spaces = new ConcurrentHashMap<>();

    public PhysicsWorldRegistry(InertiaPlugin plugin,
                                ConfigurationService configurationService,
                                PhysicsEngine physicsEngine,
                                PhysicsMetricsService metricsService) {
        this.plugin = plugin;
        this.configurationService = configurationService;
        this.physicsEngine = physicsEngine;
        this.metricsService = metricsService;
        this.systemFactory = new JoltSystemFactory();

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

        PhysicsSystem physicsSystem = systemFactory.createSystem(settings);

        TerrainAdapter terrainAdapter = null;
        if (settings.simulation().enabled()) {
            if (settings.simulation().type() == SimulationType.FLOOR_PLANE) {
                terrainAdapter = new FlatFloorAdapter(settings.simulation().floorPlane());
            } else if (settings.simulation().type() == SimulationType.GREEDY_MESH) {
                terrainAdapter = new GreedyMeshAdapter();
            }
        }

        return new PhysicsWorld(
                world,
                settings,
                physicsSystem,
                physicsEngine.getJobSystem(),
                physicsEngine.getTempAllocator(),
                terrainAdapter,
                metricsService
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
            }
        }
    }

    public void removeSpace(World world) {
        PhysicsWorld space = spaces.remove(world.getUID());
        if (space != null) {
            space.close();
        }
    }


    public void applyThreadingSettings(com.ladakx.inertia.configuration.dto.InertiaConfig.ThreadingSettings threadingSettings) {
        for (PhysicsWorld space : spaces.values()) {
            try {
                space.applyThreadingSettings(threadingSettings);
            } catch (Exception e) {
                InertiaLogger.error("Failed to apply threading settings for world: " + space.getBukkitWorld().getName(), e);
            }
        }
    }

    public void reload() {
        InertiaLogger.info("Reloading Physics World Registry...");

        Map<String, WorldsConfig.WorldProfile> configuredWorlds = configurationService.getWorldsConfig().getAllWorlds();

        for (Map.Entry<UUID, PhysicsWorld> entry : new java.util.ArrayList<>(spaces.entrySet())) {
            PhysicsWorld space = entry.getValue();
            World world = space.getBukkitWorld();
            WorldsConfig.WorldProfile updatedProfile = configuredWorlds.get(world.getName());

            if (updatedProfile == null) {
                try {
                    space.close();
                } catch (Exception e) {
                    InertiaLogger.error("Error closing removed world during reload: " + world.getName(), e);
                }
                spaces.remove(entry.getKey());
                continue;
            }

            if (!space.getSettings().equals(updatedProfile)) {
                try {
                    space.close();
                } catch (Exception e) {
                    InertiaLogger.error("Error closing changed world during reload: " + world.getName(), e);
                }
                try {
                    PhysicsWorld recreated = createSpaceInternal(world);
                    spaces.put(entry.getKey(), recreated);
                } catch (Exception e) {
                    spaces.remove(entry.getKey());
                    InertiaLogger.error("Failed to recreate physics world during reload: " + world.getName(), e);
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            if (!configuredWorlds.containsKey(world.getName())) {
                continue;
            }
            spaces.computeIfAbsent(world.getUID(), ignored -> {
                try {
                    return createSpaceInternal(world);
                } catch (Exception e) {
                    InertiaLogger.error("Failed to initialize new physics world during reload: " + world.getName(), e);
                    return null;
                }
            });
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
