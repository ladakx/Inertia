package com.ladakx.inertia.physics.entity;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RotatedTranslatedShape;
import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.api.entity.IEntityPhysicsManager;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EntityPhysicsManager implements IEntityPhysicsManager {
    private static final float MIN_APPROACH_SPEED = 0.45f;
    private static final float MIN_SOURCE_SPEED = 0.35f;
    private static final float MIN_IMPULSE_THRESHOLD = 2.5f;
    private static final float MAX_EFFECTIVE_IMPULSE = 18.0f;
    private static final double MAX_KNOCKBACK = 0.95d;
    private static final double MIN_VERTICAL_PUSH = 0.08d;

    private final PhysicsWorld world;
    private final PhysicsTaskManager taskManager;
    private final Map<UUID, ProxyState> proxies;
    private final Map<Integer, UUID> bodyToEntity;
    private final ConcurrentLinkedQueue<CollisionFeedbackDTO> feedbackQueue;
    private final Shape standingShape;
    private final Shape sneakingShape;
    private final Shape horizontalShape;

    public EntityPhysicsManager(PhysicsWorld world, PhysicsTaskManager taskManager) {
        this.world = Objects.requireNonNull(world);
        this.taskManager = Objects.requireNonNull(taskManager);
        this.proxies = new ConcurrentHashMap<>();
        this.bodyToEntity = new ConcurrentHashMap<>();
        this.feedbackQueue = new ConcurrentLinkedQueue<>();
        this.standingShape = new CapsuleShape(0.9f, 0.3f);
        this.sneakingShape = new CapsuleShape(0.75f, 0.3f);
        CapsuleShape base = new CapsuleShape(0.75f, 0.3f);
        Quat rotation = ConvertUtils.toJolt(new org.joml.Quaternionf().rotationXYZ(0f, 0f, (float) (Math.PI * 0.5d)));
        this.horizontalShape = new RotatedTranslatedShape(new Vec3(0f, 0f, 0f), rotation, base);
    }

    @Override
    public void track(Entity entity) {
        requireEntity(entity);
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        proxies.computeIfAbsent(entity.getUniqueId(), ignored -> new ProxyState(entity.getUniqueId()));
    }

    @Override
    public void untrack(UUID entityId) {
        requireEntityId(entityId);
        ProxyState state = proxies.remove(entityId);
        if (state == null || state.bodyId < 0) {
            return;
        }
        int bodyId = state.bodyId;
        bodyToEntity.remove(bodyId);
        taskManager.schedule(() -> {
            BodyInterface bodyInterface = world.getBodyInterface();
            bodyInterface.removeBody(bodyId);
            bodyInterface.destroyBody(bodyId);
        });
    }

    public void syncFromBukkit() {
        for (LivingEntity entity : world.getWorldBukkit().getLivingEntities()) {
            if (!entity.isValid() || entity.isDead()) {
                continue;
            }
            track(entity);
            scheduleSync(entity);
        }
        for (Map.Entry<UUID, ProxyState> entry : proxies.entrySet()) {
            Entity entity = world.getWorldBukkit().getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                untrack(entry.getKey());
            }
        }
    }

    public void drainFeedback() {
        CollisionFeedbackDTO dto;
        while ((dto = feedbackQueue.poll()) != null) {
            Entity entity = world.getWorldBukkit().getEntity(dto.entityId());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            living.setVelocity(dto.knockback());
            double damage = Math.min(8.0d, dto.impulse() * 0.025d);
            if (damage > 0.0d) {
                living.damage(damage);
            }
        }
    }

    public void handleDynamicContact(int bodyA,
                                     int bodyB,
                                     RVec3 positionA,
                                     RVec3 positionB,
                                     Vec3 velocityA,
                                     Vec3 velocityB,
                                     float massA,
                                     float massB) {
        Objects.requireNonNull(positionA);
        Objects.requireNonNull(positionB);
        Objects.requireNonNull(velocityA);
        Objects.requireNonNull(velocityB);
        process(bodyA, bodyB, positionA, positionB, velocityA, velocityB, massB);
        process(bodyB, bodyA, positionB, positionA, velocityB, velocityA, massA);
    }

    private void process(int entityBodyId,
                         int otherBodyId,
                         RVec3 entityPosition,
                         RVec3 otherPosition,
                         Vec3 entityVelocity,
                         Vec3 otherVelocity,
                         float otherMass) {
        UUID entityId = bodyToEntity.get(entityBodyId);
        if (entityId == null) {
            return;
        }
        float sourceSpeed = length(otherVelocity);
        if (sourceSpeed < MIN_SOURCE_SPEED) {
            return;
        }
        float dirX = (float) (entityPosition.xx() - otherPosition.xx());
        float dirY = (float) (entityPosition.yy() - otherPosition.yy());
        float dirZ = (float) (entityPosition.zz() - otherPosition.zz());
        float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen <= 0.0001f) {
            return;
        }
        float nx = dirX / dirLen;
        float ny = dirY / dirLen;
        float nz = dirZ / dirLen;

        float relX = otherVelocity.getX() - entityVelocity.getX();
        float relY = otherVelocity.getY() - entityVelocity.getY();
        float relZ = otherVelocity.getZ() - entityVelocity.getZ();
        float approachSpeed = relX * nx + relY * ny + relZ * nz;
        if (approachSpeed < MIN_APPROACH_SPEED) {
            return;
        }

        float impulse = otherMass * approachSpeed;
        if (impulse < MIN_IMPULSE_THRESHOLD) {
            return;
        }
        float effectiveImpulse = Math.min(MAX_EFFECTIVE_IMPULSE, impulse);
        Vector knockbackDirection = new Vector(nx, Math.max(MIN_VERTICAL_PUSH, ny), nz).normalize();
        double knockbackPower = Math.min(MAX_KNOCKBACK, 0.06d + effectiveImpulse * 0.028d);
        Vector knockback = knockbackDirection.multiply(knockbackPower);

        feedbackQueue.offer(new CollisionFeedbackDTO(entityId, knockback, effectiveImpulse));
    }

    private float length(Vec3 velocity) {
        return (float) Math.sqrt(
                velocity.getX() * velocity.getX()
                        + velocity.getY() * velocity.getY()
                        + velocity.getZ() * velocity.getZ()
        );
    }

    private void scheduleSync(LivingEntity entity) {
        Objects.requireNonNull(entity);
        ProxyState state = proxies.get(entity.getUniqueId());
        if (state == null) {
            return;
        }
        Location location = entity.getLocation();
        PoseState poseState = resolvePose(entity);
        RVec3 targetPosition = ConvertUtils.toRVec3(location);
        Quat targetRotation = ConvertUtils.toJolt(new org.joml.Quaternionf().rotationYXZ((float) Math.toRadians(location.getYaw()), (float) Math.toRadians(location.getPitch()), 0f));

        taskManager.schedule(() -> {
            BodyInterface bodyInterface = world.getBodyInterface();
            if (state.bodyId < 0) {
                BodyCreationSettings settings = new BodyCreationSettings();
                settings.setObjectLayer(PhysicsLayers.OBJ_ENTITY);
                settings.setMotionType(EMotionType.Kinematic);
                settings.setShape(shapeFor(poseState));
                settings.setPosition(targetPosition);
                settings.setRotation(targetRotation);
                Body body = bodyInterface.createBody(settings);
                if (body == null) {
                    return;
                }
                int bodyId = body.getId();
                bodyInterface.addBody(bodyId, EActivation.Activate);
                state.bodyId = bodyId;
                state.poseState = poseState;
                bodyToEntity.put(bodyId, state.entityId);
            } else {
                if (state.poseState != poseState) {
                    bodyInterface.setShape(state.bodyId, shapeFor(poseState), true, EActivation.Activate);
                    state.poseState = poseState;
                }
                bodyInterface.moveKinematic(state.bodyId, targetPosition, targetRotation, 1.0f / Math.max(1, world.getSettings().tickRate()));
            }
        });
    }

    private Shape shapeFor(PoseState poseState) {
        if (poseState == PoseState.SNEAKING) {
            return sneakingShape;
        }
        if (poseState == PoseState.HORIZONTAL) {
            return horizontalShape;
        }
        return standingShape;
    }

    private PoseState resolvePose(LivingEntity entity) {
        Objects.requireNonNull(entity);
        if (entity.isGliding() || entity.isSwimming()) {
            return PoseState.HORIZONTAL;
        }
        if (entity.isSneaking()) {
            return PoseState.SNEAKING;
        }
        return PoseState.STANDING;
    }

    public boolean isEntityProxy(int bodyId) {
        return bodyToEntity.containsKey(bodyId);
    }

    private enum PoseState {
        STANDING,
        SNEAKING,
        HORIZONTAL
    }

    private static final class ProxyState {
        private final UUID entityId;
        private volatile int bodyId;
        private volatile PoseState poseState;

        private ProxyState(UUID entityId) {
            this.entityId = entityId;
            this.bodyId = -1;
            this.poseState = PoseState.STANDING;
        }
    }

    public record CollisionFeedbackDTO(UUID entityId, Vector knockback, float impulse) {
    }
}
