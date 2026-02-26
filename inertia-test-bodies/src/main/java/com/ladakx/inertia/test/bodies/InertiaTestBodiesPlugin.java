package com.ladakx.inertia.test.bodies;

import com.github.stephengold.joltjni.Quat;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApi;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.physics.BoxShape;
import com.ladakx.inertia.api.physics.CylinderShape;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
import com.ladakx.inertia.api.physics.PhysicsShape;
import com.ladakx.inertia.api.physics.SphereShape;
import com.ladakx.inertia.api.rendering.entity.RenderModelInstance;
import com.ladakx.inertia.api.rendering.model.RenderIdPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InertiaTestBodiesPlugin extends JavaPlugin {

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private volatile int syncTaskId = -1;
    private volatile InertiaApi api;

    @Override
    public void onEnable() {
        api = InertiaApiAccess.resolve();

        var renderCfg = api.configs().loadYaml(this, "render.yml");
        api.renderModels().registerFromConfigSection(this, renderCfg, RenderIdPolicy.NAMESPACE_OWNER_IF_MISSING);

        syncTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::syncTick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
        }
        for (Entry e : entries.values()) {
            try {
                e.model.close();
            } catch (Exception ignored) {
            }
        }
        entries.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("itb")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage("Usage: /itb spawn <light_cube|heavy_cube|bouncy_ball|slippery_puck>");
            return true;
        }

        String preset = args[1].toLowerCase(Locale.ROOT);
        Preset p = Preset.of(preset);
        if (p == null) {
            sender.sendMessage("Unknown preset: " + preset);
            return true;
        }

        var pw = api.getPhysicsWorld(player.getWorld());
        if (pw == null) {
            sender.sendMessage("This world is not simulated by Inertia.");
            return true;
        }

        Location loc = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2.0));
        PhysicsBodySpec spec = PhysicsBodySpec.builder(loc, p.shape)
                .mass(p.mass)
                .friction(p.friction)
                .restitution(p.restitution)
                .linearDamping(p.linearDamping)
                .angularDamping(p.angularDamping)
                .build();

        ApiResult<PhysicsBody> spawned = api.bodies().spawn(this, pw, spec);
        if (!spawned.isSuccess() || spawned.getValue() == null) {
            sender.sendMessage("Spawn failed: " + (spawned.getErrorCode() == null ? "unknown" : spawned.getErrorCode().name()));
            return true;
        }

        String modelId = getName() + ":" + p.renderModelId;
        RenderModelInstance model;
        try {
            model = api.rendering().entities().createModel(player.getWorld(), loc, new Quaternionf(), modelId);
        } catch (Exception e) {
            try {
                spawned.getValue().destroy();
            } catch (Exception ignored) {
            }
            sender.sendMessage("Render create failed: " + e.getMessage());
            return true;
        }

        UUID id = UUID.randomUUID();
        entries.put(id, new Entry(id, pw, spawned.getValue(), model));
        sender.sendMessage("Spawned: " + preset + " (" + id + ")");
        return true;
    }

    private void syncTick() {
        InertiaApi a = api;
        if (a == null) return;
        if (entries.isEmpty()) return;

        // Group by world to reduce physics-thread hops.
        var byWorld = new java.util.HashMap<com.ladakx.inertia.api.world.PhysicsWorld, List<Entry>>();
        for (Entry e : entries.values()) {
            byWorld.computeIfAbsent(e.world, k -> new java.util.ArrayList<>()).add(e);
        }

        for (var batch : byWorld.entrySet()) {
            var world = batch.getKey();
            var list = batch.getValue();
            a.jolt().submitRead(world, ctx -> {
                Snapshot[] out = new Snapshot[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Entry e = list.get(i);
                    Location loc = e.body.getLocation();
                    Quat rot = ctx.bodyOf(e.body).getRotation();
                    out[i] = new Snapshot(e.id, loc, new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
                }
                return out;
            }).thenAccept(snapshots -> Bukkit.getScheduler().runTask(this, () -> {
                for (Snapshot s : snapshots) {
                    Entry e = entries.get(s.id);
                    if (e == null) continue;
                    e.model.setBaseTransform(s.location, s.rotation);
                    e.model.sync();
                }
            }));
        }
    }

    private record Entry(UUID id,
                         com.ladakx.inertia.api.world.PhysicsWorld world,
                         PhysicsBody body,
                         RenderModelInstance model) {
        private Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(model, "model");
        }
    }

    private record Snapshot(UUID id, Location location, Quaternionf rotation) {}

    private static final class Preset {
        private final String renderModelId;
        private final PhysicsShape shape;
        private final float mass;
        private final float friction;
        private final float restitution;
        private final float linearDamping;
        private final float angularDamping;

        private Preset(String renderModelId,
                       PhysicsShape shape,
                       float mass,
                       float friction,
                       float restitution,
                       float linearDamping,
                       float angularDamping) {
            this.renderModelId = renderModelId;
            this.shape = shape;
            this.mass = mass;
            this.friction = friction;
            this.restitution = restitution;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
        }

        static Preset of(String id) {
            return switch (id) {
                case "light_cube" -> new Preset("light_cube", new BoxShape(0.5f, 0.5f, 0.5f), 2.0f, 0.6f, 0.05f, 0.02f, 0.02f);
                case "heavy_cube" -> new Preset("heavy_cube", new BoxShape(0.5f, 0.5f, 0.5f), 250.0f, 0.9f, 0.0f, 0.05f, 0.05f);
                case "bouncy_ball" -> new Preset("bouncy_ball", new SphereShape(0.4f), 6.0f, 0.15f, 0.9f, 0.01f, 0.01f);
                case "slippery_puck" -> new Preset("slippery_puck", new CylinderShape(0.2f, 0.45f), 12.0f, 0.02f, 0.02f, 0.01f, 0.01f);
                default -> null;
            };
        }
    }
}
