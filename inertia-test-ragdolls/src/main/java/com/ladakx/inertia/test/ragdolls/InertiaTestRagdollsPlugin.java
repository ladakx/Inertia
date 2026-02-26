package com.ladakx.inertia.test.ragdolls;

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

public final class InertiaTestRagdollsPlugin extends JavaPlugin {

    private final ConcurrentMap<UUID, RagdollInstance> ragdolls = new ConcurrentHashMap<>();
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
        for (RagdollInstance ragdoll : ragdolls.values()) {
            try {
                if (a != null && ragdoll.joints != null) {
                    a.jolt().submitWrite(ragdoll.world, ctx -> {
                        TwoBodyConstraint c = ragdoll.joints.getPtr();
                        if (c != null) {
                            ctx.physicsSystem().removeConstraint(c);
                        }
                        return Boolean.TRUE;
                    }).join();
                }
            } catch (Exception ignored) {
            }
            try {
                ragdoll.torsoModel.close();
            } catch (Exception ignored) {
            }
            try {
                ragdoll.headModel.close();
            } catch (Exception ignored) {
            }
        }
        ragdolls.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("itragdoll")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage("Usage: /itragdoll spawn");
            return true;
        }

        var pw = api.getPhysicsWorld(player.getWorld());
        if (pw == null) {
            sender.sendMessage("This world is not simulated by Inertia.");
            return true;
        }

        Location base = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(2.0));
        Location torsoLoc = base.clone();
        Location headLoc = base.clone().add(0.0, 0.8, 0.0);

        ApiResult<PhysicsBody> torso = api.bodies().spawn(this, pw, PhysicsBodySpec.builder(torsoLoc, new BoxShape(0.30f, 0.45f, 0.15f))
                .mass(25.0f)
                .friction(0.6f)
                .linearDamping(0.05f)
                .angularDamping(0.05f)
                .bodyId("ragdoll:torso")
                .build());
        if (!torso.isSuccess() || torso.getValue() == null) {
            sender.sendMessage("Torso spawn failed.");
            return true;
        }

        ApiResult<PhysicsBody> head = api.bodies().spawn(this, pw, PhysicsBodySpec.builder(headLoc, new BoxShape(0.20f, 0.20f, 0.20f))
                .mass(6.0f)
                .friction(0.6f)
                .linearDamping(0.05f)
                .angularDamping(0.05f)
                .bodyId("ragdoll:head")
                .build());
        if (!head.isSuccess() || head.getValue() == null) {
            torso.getValue().destroy();
            sender.sendMessage("Head spawn failed.");
            return true;
        }

        String torsoModelId = getName() + ":torso";
        String headModelId = getName() + ":head";
        RenderModelInstance torsoModel = api.rendering().entities().createModel(player.getWorld(), torsoLoc, new Quaternionf(), torsoModelId);
        RenderModelInstance headModel = api.rendering().entities().createModel(player.getWorld(), headLoc, new Quaternionf(), headModelId);

        UUID id = UUID.randomUUID();
        RagdollInstance instance = new RagdollInstance(id, pw, torso.getValue(), head.getValue(), torsoModel, headModel, null);
        ragdolls.put(id, instance);

        api.jolt().submitWrite(pw, ctx -> {
            Body torsoBody = ctx.bodyOf(instance.torso);
            Body headBody = ctx.bodyOf(instance.head);

            RVec3 torsoPos = torsoBody.getPosition();
            // Pivot near the top of the torso.
            RVec3 pivot = new RVec3(torsoPos.xx(), torsoPos.yy() + 0.45, torsoPos.zz());

            SixDofConstraintSettings s = new SixDofConstraintSettings();
            s.setSpace(EConstraintSpace.WorldSpace);
            s.setPosition1(pivot);
            s.setPosition2(pivot);
            s.makeFixedAxis(EAxis.TranslationX);
            s.makeFixedAxis(EAxis.TranslationY);
            s.makeFixedAxis(EAxis.TranslationZ);

            float neckSwing = (float) Math.toRadians(45.0);
            s.setLimitedAxis(EAxis.RotationX, -neckSwing, neckSwing);
            s.setLimitedAxis(EAxis.RotationY, -neckSwing, neckSwing);
            s.setLimitedAxis(EAxis.RotationZ, -neckSwing, neckSwing);

            TwoBodyConstraint c = s.create(torsoBody, headBody);
            ctx.physicsSystem().addConstraint(c);
            return c.toRef();
        }).thenAccept(ref -> instance.joints = ref);

        sender.sendMessage("Spawned ragdoll: " + id);
        return true;
    }

    private void syncTick() {
        InertiaApi a = api;
        if (a == null) return;
        if (ragdolls.isEmpty()) return;

        for (RagdollInstance r : ragdolls.values()) {
            a.jolt().submitRead(r.world, ctx -> {
                Location torsoLoc = r.torso.getLocation();
                Quat torsoRot = ctx.bodyOf(r.torso).getRotation();
                Location headLoc = r.head.getLocation();
                Quat headRot = ctx.bodyOf(r.head).getRotation();
                return new Snap(
                        torsoLoc, new Quaternionf(torsoRot.getX(), torsoRot.getY(), torsoRot.getZ(), torsoRot.getW()),
                        headLoc, new Quaternionf(headRot.getX(), headRot.getY(), headRot.getZ(), headRot.getW())
                );
            }).thenAccept(s -> Bukkit.getScheduler().runTask(this, () -> {
                r.torsoModel.setBaseTransform(s.torsoLoc, s.torsoRot);
                r.torsoModel.sync();
                r.headModel.setBaseTransform(s.headLoc, s.headRot);
                r.headModel.sync();
            }));
        }
    }

    private static final class RagdollInstance {
        private final UUID id;
        private final com.ladakx.inertia.api.world.PhysicsWorld world;
        private final PhysicsBody torso;
        private final PhysicsBody head;
        private final RenderModelInstance torsoModel;
        private final RenderModelInstance headModel;
        private volatile TwoBodyConstraintRef joints;

        private RagdollInstance(UUID id,
                                com.ladakx.inertia.api.world.PhysicsWorld world,
                                PhysicsBody torso,
                                PhysicsBody head,
                                RenderModelInstance torsoModel,
                                RenderModelInstance headModel,
                                TwoBodyConstraintRef joints) {
            this.id = Objects.requireNonNull(id, "id");
            this.world = Objects.requireNonNull(world, "world");
            this.torso = Objects.requireNonNull(torso, "torso");
            this.head = Objects.requireNonNull(head, "head");
            this.torsoModel = Objects.requireNonNull(torsoModel, "torsoModel");
            this.headModel = Objects.requireNonNull(headModel, "headModel");
            this.joints = joints;
        }
    }

    private record Snap(Location torsoLoc, Quaternionf torsoRot, Location headLoc, Quaternionf headRot) {}
}

