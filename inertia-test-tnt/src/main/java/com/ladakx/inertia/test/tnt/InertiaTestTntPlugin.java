package com.ladakx.inertia.test.tnt;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.ladakx.inertia.api.ApiResult;
import com.ladakx.inertia.api.InertiaApi;
import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.body.PhysicsBody;
import com.ladakx.inertia.api.physics.BoxShape;
import com.ladakx.inertia.api.physics.PhysicsBodySpec;
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

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InertiaTestTntPlugin extends JavaPlugin {

    private final ConcurrentMap<UUID, ActiveTnt> tnts = new ConcurrentHashMap<>();
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
        for (ActiveTnt t : tnts.values()) {
            try {
                t.model.close();
            } catch (Exception ignored) {
            }
        }
        tnts.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("ittnt")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage("Usage: /ittnt spawn [fuseTicks] [force] [radius]");
            return true;
        }

        int fuseTicks = 60;
        float force = 25.0f;
        float radius = 6.0f;
        if (args.length >= 2) {
            try { fuseTicks = Math.max(1, Math.min(20 * 30, Integer.parseInt(args[1]))); } catch (Exception ignored) {}
        }
        if (args.length >= 3) {
            try { force = Math.max(0.1f, Math.min(200.0f, Float.parseFloat(args[2]))); } catch (Exception ignored) {}
        }
        if (args.length >= 4) {
            try { radius = Math.max(0.5f, Math.min(50.0f, Float.parseFloat(args[3]))); } catch (Exception ignored) {}
        }

        var pw = api.getPhysicsWorld(player.getWorld());
        if (pw == null) {
            sender.sendMessage("This world is not simulated by Inertia.");
            return true;
        }

        Location loc = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2.0));
        ApiResult<PhysicsBody> spawned = api.bodies().spawn(this, pw, PhysicsBodySpec.builder(loc, new BoxShape(0.5f, 0.5f, 0.5f))
                .mass(20.0f)
                .friction(0.6f)
                .restitution(0.05f)
                .linearDamping(0.03f)
                .angularDamping(0.03f)
                .bodyId("tnt:" + UUID.randomUUID())
                .build());
        if (!spawned.isSuccess() || spawned.getValue() == null) {
            sender.sendMessage("TNT spawn failed.");
            return true;
        }

        String modelId = getName() + ":tnt";
        RenderModelInstance model = api.rendering().entities().createModel(player.getWorld(), loc, new Quaternionf(), modelId);
        UUID id = UUID.randomUUID();
        ActiveTnt tnt = new ActiveTnt(id, pw, spawned.getValue(), model, force, radius);
        tnts.put(id, tnt);

        Bukkit.getScheduler().runTaskLater(this, () -> detonate(tnt.id), fuseTicks);
        sender.sendMessage("Spawned TNT: " + id + " fuseTicks=" + fuseTicks + " force=" + force + " radius=" + radius);
        return true;
    }

    private void detonate(UUID id) {
        ActiveTnt tnt = tnts.remove(id);
        if (tnt == null) return;
        InertiaApi a = api;
        if (a == null) return;

        try {
            a.jolt().submitWrite(tnt.world, ctx -> {
                Body center = ctx.bodyOf(tnt.body);
                if (center == null) return Boolean.FALSE;
                RVec3 c = center.getPosition();

                float r = tnt.radius;
                float r2 = r * r;

                for (PhysicsBody b : tnt.world.getBodies()) {
                    if (b == null || b == tnt.body || !b.isValid()) continue;
                    Body body = ctx.bodyOf(b);
                    if (body == null) continue;
                    RVec3 p = body.getPosition();
                    double dx = p.xx() - c.xx();
                    double dy = p.yy() - c.yy();
                    double dz = p.zz() - c.zz();
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= 1.0e-6 || d2 > r2) continue;
                    double dist = Math.sqrt(d2);
                    double k = 1.0 - (dist / r);
                    float mag = (float) (tnt.force * k);
                    float ix = (float) (dx / dist) * mag;
                    float iy = (float) (dy / dist) * mag;
                    float iz = (float) (dz / dist) * mag;
                    ctx.bodyInterface().addImpulse(body.getId(), new Vec3(ix, iy, iz));
                }
                return Boolean.TRUE;
            });
        } catch (Exception ignored) {
        }

        try {
            tnt.body.destroy();
        } catch (Exception ignored) {
        }
        try {
            tnt.model.close();
        } catch (Exception ignored) {
        }
    }

    private void syncTick() {
        InertiaApi a = api;
        if (a == null) return;
        if (tnts.isEmpty()) return;

        for (ActiveTnt t : tnts.values()) {
            a.jolt().submitRead(t.world, ctx -> {
                Location loc = t.body.getLocation();
                Quat rot = ctx.bodyOf(t.body).getRotation();
                return new Snap(loc, new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
            }).thenAccept(s -> Bukkit.getScheduler().runTask(this, () -> {
                t.model.setBaseTransform(s.loc, s.rot);
                t.model.sync();
            }));
        }
    }

    private static final class ActiveTnt {
        private final UUID id;
        private final com.ladakx.inertia.api.world.PhysicsWorld world;
        private final PhysicsBody body;
        private final RenderModelInstance model;
        private final float force;
        private final float radius;

        private ActiveTnt(UUID id,
                          com.ladakx.inertia.api.world.PhysicsWorld world,
                          PhysicsBody body,
                          RenderModelInstance model,
                          float force,
                          float radius) {
            this.id = Objects.requireNonNull(id, "id");
            this.world = Objects.requireNonNull(world, "world");
            this.body = Objects.requireNonNull(body, "body");
            this.model = Objects.requireNonNull(model, "model");
            this.force = force;
            this.radius = radius;
        }
    }

    private record Snap(Location loc, Quaternionf rot) {}
}

