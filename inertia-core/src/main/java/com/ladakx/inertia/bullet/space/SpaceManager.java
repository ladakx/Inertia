package com.ladakx.inertia.bullet.space;

import com.jme3.bullet.PhysicsSpace;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.events.PhysicsLoadSpaceEvent;
import com.ladakx.inertia.api.events.PhysicsUnloadSpaceEvent;
import com.ladakx.inertia.files.config.PluginCFG;
import com.ladakx.inertia.performance.pool.PhysicsThread;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SpaceManager {

    private final BukkitScheduler scheduler;
    private final Map<World, MinecraftSpace> spaces;

    public SpaceManager() {
        this.scheduler = Bukkit.getScheduler();
        this.spaces = new HashMap<>();
    }

    public void load(World world) {
        if (InertiaPlugin.getPConfig().PHYSICS.WORLDS.getWorld(world.getName()) != null) {
            // create space
            InertiaPlugin.logInfo("Loading physics space for world: " + world.getName());
            createSpaceAsync(world);

            // call event
            scheduler.runTask(InertiaPlugin.getInstance(), () -> {
                PhysicsLoadSpaceEvent event = new PhysicsLoadSpaceEvent(spaces.get(world));
                Bukkit.getPluginManager().callEvent(event);
            });
        }
    }

    public void unload(World world) {
        InertiaPlugin.logInfo("Unloading physics space for world: " + world.getName());

        // call event
        PhysicsUnloadSpaceEvent event = new PhysicsUnloadSpaceEvent(spaces.get(world));
        Bukkit.getPluginManager().callEvent(event);

        // unload space
        MinecraftSpace space = spaces.remove(world);
        space.destroy();
    }

    public void loadSpaces() {
        for (World world : Bukkit.getWorlds()) {
            load(world);
        }
    }

    public void unloadSpaces() {
        for (MinecraftSpace space : spaces.values()) {
            space.shutdown();
        }

        spaces.clear();
    }

    public boolean hasSpace(World world) {
        return spaces.containsKey(world);
    }

    public MinecraftSpace getSpace(World world) {
        return spaces.get(world);
    }

    public CompletableFuture<MinecraftSpace> createSpaceAsync(World world) {
        if (spaces.containsKey(world)) {
            return CompletableFuture.completedFuture(spaces.get(world));
        }

        if (InertiaPlugin.getPConfig().PHYSICS.WORLDS.getWorld(world.getName()) == null) {
            return CompletableFuture.completedFuture(null);
        }

        PluginCFG.Physics.Worlds.World settings = InertiaPlugin.getPConfig().PHYSICS.WORLDS.getWorld(world.getName());

        PhysicsThread thread = new PhysicsThread(world.getName());
        CompletableFuture<MinecraftSpace> future = new CompletableFuture<>();

        thread.execute(() -> {
            try {
                MinecraftSpace space = new MinecraftSpace(
                        world,
                        thread,
                        settings.maxPoint,
                        settings.minPoint,
                        PhysicsSpace.BroadphaseType.DBVT,
                        settings.solverType
                );
                add(world, space);
                future.complete(space);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void reloadSpaces() {
        for (MinecraftSpace space : spaces.values()) {
            space.reload();
        }
    }

    public Collection<MinecraftSpace> getAll() {
        return spaces.values();
    }

    public void add(World level, MinecraftSpace minecraftSpace) {
        spaces.put(level, minecraftSpace);
    }

    // *************************************
    // Debug
    private final Set<UUID> playersDebug = new HashSet<>();

    public void toggleDebugBar(Player player) {
        for (MinecraftSpace space : getAll()) {
            Audience aud = InertiaPlugin.getAdventure().player(player);
            boolean toggled = playersDebug.contains(player.getUniqueId());

            if (toggled) {
                playersDebug.remove(player.getUniqueId());
                space.getDebugBar().removeViewer(aud);
            } else {
                playersDebug.add(player.getUniqueId());
                space.getDebugBar().addViewer(aud);
            }
        }
    }

    public void disableDebugBar(Player player) {
        playersDebug.remove(player.getUniqueId());
    }

    public void enableDebugBar(Player player) {
        playersDebug.add(player.getUniqueId());
    }

    public Set<UUID> getPlayersDebug() {
        return playersDebug;
    }
}
