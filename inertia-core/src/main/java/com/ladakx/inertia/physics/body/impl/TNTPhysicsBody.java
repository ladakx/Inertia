package com.ladakx.inertia.physics.body.impl;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.physics.body.registry.PhysicsBodyRegistry;
import com.ladakx.inertia.common.utils.ConvertUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represents a physics-based TNT entity.
 * Handles the fuse timer and triggers the Jolt explosion.
 */
public class TNTPhysicsBody extends BlockPhysicsBody {

    private final float explosionForce;
    private final float explosionRadius;
    private int fuseTicks;
    private final UUID tickTaskUuid;

    public TNTPhysicsBody(@NotNull PhysicsWorld space,
                          @NotNull String bodyId,
                          @NotNull PhysicsBodyRegistry modelRegistry,
                          @NotNull RenderFactory renderFactory,
                          @NotNull RVec3 initialPosition,
                          @NotNull Quat initialRotation,
                          float explosionForce,
                          int fuseTicks) {
        super(space, bodyId, modelRegistry, renderFactory, initialPosition, initialRotation);
        this.explosionForce = explosionForce;
        this.fuseTicks = fuseTicks;
        // Radius typically scales with force, or can be fixed. 
        // For Minecraft-like feel, force 4.0 has radius ~4-5 blocks. 
        // We'll estimate radius based on force.
        this.explosionRadius = Math.max(4.0f, explosionForce / 2.0f);

        // Schedule physics logic
        this.tickTaskUuid = space.addTickTask(this::onPhysicsTick);
    }

    /**
     * Executed on the Physics Thread every simulation step.
     */
    private void onPhysicsTick() {
        if (!isValid()) {
            getSpace().removeTickTask(tickTaskUuid);
            return;
        }

        fuseTicks--;

        if (fuseTicks <= 0) {
            detonate();
        }
    }

    private void detonate() {
        // 1. Remove tick task to stop counting
        getSpace().removeTickTask(tickTaskUuid);

        // 2. Perform Physics Explosion (Thread-safe Jolt calls)
        // Convert current RVec3 (Double) to Vec3 (Float) for calculation
        RVec3 currentPosR = getBody().getPosition();
        com.github.stephengold.joltjni.Vec3 origin = new com.github.stephengold.joltjni.Vec3(
                (float) currentPosR.xx(),
                (float) currentPosR.yy(),
                (float) currentPosR.zz()
        );

        getSpace().createExplosion(origin, explosionForce, explosionRadius);

        // 3. Schedule Visuals & Cleanup on Main Thread
        // We capture location data before destroying the body wrapper logic
        Location explodeLoc = ConvertUtils.toBukkitLoc(currentPosR, getSpace().getWorldBukkit());

        Bukkit.getScheduler().runTask(InertiaPlugin.getInstance(), () -> {
            // Visual Effects
            explodeLoc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, explodeLoc, 1);
            explodeLoc.getWorld().playSound(explodeLoc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0f, 1.0f);

            // Destroy the object (removes Jolt body and Display entity)
            this.destroy();
        });
    }
}