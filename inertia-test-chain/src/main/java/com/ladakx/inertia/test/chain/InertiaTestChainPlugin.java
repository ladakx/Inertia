package com.ladakx.inertia.test.chain;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintRef;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InertiaTestChainPlugin extends JavaPlugin {

    private final ConcurrentMap<UUID, ChainInstance> chains = new ConcurrentHashMap<>();
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
        InertiaApi a = api;
        for (ChainInstance chain : chains.values()) {
            try {
                if (a != null && !chain.constraints.isEmpty()) {
                    a.jolt().submitWrite(chain.world, ctx -> {
                        for (TwoBodyConstraintRef ref : chain.constraints) {
                            if (ref == null) continue;
                            TwoBodyConstraint c = ref.getPtr();
                            if (c != null) {
                                ctx.physicsSystem().removeConstraint(c);
                            }
                        }
                        return Boolean.TRUE;
                    }).join();
                }
            } catch (Exception ignored) {
            }
            for (RenderModelInstance model : chain.models) {
                try {
                    model.close();
                } catch (Exception ignored) {
                }
            }
        }
        chains.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("itchain")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage("Usage: /itchain spawn [links]");
            return true;
        }
        int links = 12;
        if (args.length >= 2) {
            try {
                links = Math.max(2, Math.min(80, Integer.parseInt(args[1])));
            } catch (Exception ignored) {
            }
        }

        var pw = api.getPhysicsWorld(player.getWorld());
        if (pw == null) {
            sender.sendMessage("This world is not simulated by Inertia.");
            return true;
        }

        Location base = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2.0));
        float spacing = 0.55f;
        float jointOffset = 0.25f;

        List<PhysicsBody> bodies = new ArrayList<>(links);
        List<RenderModelInstance> models = new ArrayList<>(links);
        String modelId = getName() + ":link";

        for (int i = 0; i < links; i++) {
            Location loc = base.clone().add(0.0, -i * spacing, 0.0);
            PhysicsBodySpec spec = PhysicsBodySpec.builder(loc, new BoxShape(0.08f, 0.25f, 0.08f))
                    .mass(8.0f)
                    .friction(0.6f)
                    .restitution(0.0f)
                    .linearDamping(0.6f)
                    .angularDamping(0.6f)
                    .bodyId("chain:" + i)
                    .build();

            ApiResult<PhysicsBody> spawned = api.bodies().spawn(this, pw, spec);
            if (!spawned.isSuccess() || spawned.getValue() == null) {
                sender.sendMessage("Spawn failed at link " + i);
                break;
            }
            bodies.add(spawned.getValue());

            RenderModelInstance model = api.rendering().entities().createModel(player.getWorld(), loc, new Quaternionf(), modelId);
            models.add(model);
        }

        if (bodies.size() < 2) {
            for (RenderModelInstance m : models) {
                try {
                    m.close();
                } catch (Exception ignored) {
                }
            }
            for (PhysicsBody b : bodies) {
                try {
                    b.destroy();
                } catch (Exception ignored) {
                }
            }
            return true;
        }

        UUID chainId = UUID.randomUUID();
        ChainInstance instance = new ChainInstance(chainId, pw, bodies, models, new ArrayList<>());
        chains.put(chainId, instance);

        api.jolt().submitWrite(pw, ctx -> {
            ArrayList<TwoBodyConstraintRef> refs = new ArrayList<>(bodies.size() - 1);
            for (int i = 1; i < bodies.size(); i++) {
                Body parent = ctx.bodyOf(bodies.get(i - 1));
                Body child = ctx.bodyOf(bodies.get(i));

                RVec3 childPos = child.getPosition();
                RVec3 parentPos = parent.getPosition();
                double dx = parentPos.xx() - childPos.xx();
                double dy = parentPos.yy() - childPos.yy();
                double dz = parentPos.zz() - childPos.zz();

                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0e-6) {
                    dx = 0.0;
                    dy = 1.0;
                    dz = 0.0;
                    len = 1.0;
                }

                double nx = dx / len;
                double ny = dy / len;
                double nz = dz / len;

                RVec3 pivot = new RVec3(
                        childPos.xx() + nx * jointOffset,
                        childPos.yy() + ny * jointOffset,
                        childPos.zz() + nz * jointOffset
                );

                SixDofConstraintSettings settings = new SixDofConstraintSettings();
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setPosition1(pivot);
                settings.setPosition2(pivot);
                settings.makeFixedAxis(EAxis.TranslationX);
                settings.makeFixedAxis(EAxis.TranslationY);
                settings.makeFixedAxis(EAxis.TranslationZ);

                float swingRad = (float) Math.toRadians(55.0);
                settings.setLimitedAxis(EAxis.RotationX, -swingRad, swingRad);
                settings.setLimitedAxis(EAxis.RotationZ, -swingRad, swingRad);
                settings.makeFreeAxis(EAxis.RotationY);

                TwoBodyConstraint constraint = settings.create(parent, child);
                ctx.physicsSystem().addConstraint(constraint);
                refs.add(constraint.toRef());
            }
            return refs;
        }).thenAccept(refs -> instance.constraints.addAll(refs));

        sender.sendMessage("Spawned chain: " + chainId + " links=" + bodies.size());
        return true;
    }

    private void syncTick() {
        InertiaApi a = api;
        if (a == null) return;
        if (chains.isEmpty()) return;

        for (ChainInstance chain : chains.values()) {
            List<PhysicsBody> bodies = chain.bodies;
            List<RenderModelInstance> models = chain.models;
            if (bodies.isEmpty() || models.isEmpty()) continue;

            a.jolt().submitRead(chain.world, ctx -> {
                Snapshot[] out = new Snapshot[bodies.size()];
                for (int i = 0; i < bodies.size(); i++) {
                    PhysicsBody b = bodies.get(i);
                    Location loc = b.getLocation();
                    Quat rot = ctx.bodyOf(b).getRotation();
                    out[i] = new Snapshot(i, loc, new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW()));
                }
                return out;
            }).thenAccept(snapshots -> Bukkit.getScheduler().runTask(this, () -> {
                for (Snapshot s : snapshots) {
                    if (s.index < 0 || s.index >= models.size()) continue;
                    RenderModelInstance model = models.get(s.index);
                    model.setBaseTransform(s.location, s.rotation);
                    model.sync();
                }
            }));
        }
    }

    private record ChainInstance(UUID id,
                                 com.ladakx.inertia.api.world.PhysicsWorld world,
                                 List<PhysicsBody> bodies,
                                 List<RenderModelInstance> models,
                                 List<TwoBodyConstraintRef> constraints) {
        private ChainInstance {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(bodies, "bodies");
            Objects.requireNonNull(models, "models");
            Objects.requireNonNull(constraints, "constraints");
        }
    }

    private record Snapshot(int index, Location location, Quaternionf rotation) {}
}
