package com.ladakx.inertia.physics.entity;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EActiveEdgeMode;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.api.entity.IEntityPhysicsManager;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.physics.engine.PhysicsLayers;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EntityPhysicsManager implements IEntityPhysicsManager {

    private final PhysicsWorld world;
    private final PhysicsTaskManager taskManager;
    private final Map<UUID, ProxyState> proxies = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> bodyToEntity = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CollisionFeedbackDTO> feedbackQueue = new ConcurrentLinkedQueue<>();

    private final Shape standingShape;
    private final Shape sneakingShape;
    private final Shape horizontalShape;

    private final Shape standingInsideShape;
    private final Shape sneakingInsideShape;
    private final Shape horizontalInsideShape;

    public EntityPhysicsManager(PhysicsWorld world, PhysicsTaskManager taskManager) {
        this.world = Objects.requireNonNull(world);
        this.taskManager = Objects.requireNonNull(taskManager);
        var entityCollisions = world.getSettings().entityCollisions();
        var capsules = entityCollisions.capsules();

        this.standingShape = new CapsuleShape(capsules.standing().halfHeight(), capsules.standing().radius());
        this.sneakingShape = new CapsuleShape(capsules.sneaking().halfHeight(), capsules.sneaking().radius());
        CapsuleShape base = new CapsuleShape(capsules.horizontal().halfHeight(), capsules.horizontal().radius());
        Quat rotation = ConvertUtils.toJolt(new org.joml.Quaternionf().rotationXYZ(0f, 0f, (float) (Math.PI * 0.5d)));
        this.horizontalShape = new RotatedTranslatedShape(new Vec3(0f, 0f, 0f), rotation, base);

        // Smaller shapes used to detect "deep" penetration into dynamic bodies (vs. normal touching contact).
        this.standingInsideShape = new CapsuleShape(capsules.insideStanding().halfHeight(), capsules.insideStanding().radius());
        this.sneakingInsideShape = new CapsuleShape(capsules.insideSneaking().halfHeight(), capsules.insideSneaking().radius());
        CapsuleShape insideBase = new CapsuleShape(capsules.insideHorizontal().halfHeight(), capsules.insideHorizontal().radius());
        this.horizontalInsideShape = new RotatedTranslatedShape(new Vec3(0f, 0f, 0f), rotation, insideBase);
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
        if (!world.getSettings().entityCollisions().enabled()) {
            for (UUID entityId : new ArrayList<>(proxies.keySet())) {
                untrack(entityId);
            }
            return;
        }
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
            var impact = world.getSettings().entityCollisions().impact();
            double damage = Math.min(impact.maxDamage(), dto.impulse() * impact.damagePerImpulse());
            if (damage > 0.0d) {
                living.damage(damage);
            }
        }
    }

    public void handleDynamicContact(int bodyA,
                                     int bodyB,
                                     Vec3 velocityA,
                                     Vec3 velocityB,
                                     float massA,
                                     float massB,
                                     boolean bodyASensor,
                                     boolean bodyBSensor) {
        process(bodyA, bodyB, velocityA, velocityB, massA, massB, bodyASensor);
        process(bodyB, bodyA, velocityB, velocityA, massB, massA, bodyBSensor);
    }

    private void process(int entityBodyId,
                         int otherBodyId,
                         Vec3 entityVelocity,
                         Vec3 otherVelocity,
                         float entityMass,
                         float otherMass,
                         boolean entitySensor) {
        if (entitySensor) {
            return;
        }
        UUID entityId = bodyToEntity.get(entityBodyId);
        if (entityId == null) {
            return;
        }
        var impact = world.getSettings().entityCollisions().impact();
        if (!impact.enabled()) {
            return;
        }

        float relX = otherVelocity.getX() - entityVelocity.getX();
        float relY = otherVelocity.getY() - entityVelocity.getY();
        float relZ = otherVelocity.getZ() - entityVelocity.getZ();

        float otherX = otherVelocity.getX();
        float otherY = otherVelocity.getY();
        float otherZ = otherVelocity.getZ();
        float otherSpeed = (float) Math.sqrt(otherX * otherX + otherY * otherY + otherZ * otherZ);

        // "Push" case: the entity moves into a mostly-stationary dynamic body.
        // We only want feedback (knockback/damage) when the *other* body is actually moving into the entity.
        if (otherSpeed < 1.0e-3f) {
            return;
        }
        float approachDot = otherX * relX + otherY * relY + otherZ * relZ;
        if (approachDot <= 0f) {
            return;
        }

        float closingSpeed = approachDot / otherSpeed;
        float impulse = otherMass * closingSpeed * impact.impulseScale();
        if (impulse < impact.minImpulseThreshold()) {
            return;
        }

        org.bukkit.util.Vector direction = new org.bukkit.util.Vector(otherX / otherSpeed, otherY / otherSpeed, otherZ / otherSpeed);
        direction.setY(Math.max(0.2d, direction.getY()));
        if (direction.lengthSquared() > 1.0e-8d) {
            direction.normalize();
        } else {
            direction.setX(0d);
            direction.setY(0.2d);
            direction.setZ(0d);
        }

        org.bukkit.util.Vector knockback = direction.multiply(Math.min(impact.maxKnockback(), impulse * impact.knockbackPerImpulse()));
        feedbackQueue.offer(new CollisionFeedbackDTO(entityId, knockback, impulse));
    }

    private void scheduleSync(LivingEntity entity) {
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
            boolean isPlayer = entity instanceof Player;
            var entityCollisions = world.getSettings().entityCollisions();
            if (state.bodyId < 0) {
                BodyCreationSettings settings = new BodyCreationSettings();
                settings.setObjectLayer(PhysicsLayers.OBJ_ENTITY);
                settings.setMotionType(EMotionType.Kinematic);
                settings.setShape(shapeFor(poseState));
                settings.setPosition(targetPosition);
                settings.setRotation(targetRotation);
                settings.setFriction(entityCollisions.pushing().playerProxyFriction());
                settings.setRestitution(entityCollisions.pushing().playerProxyRestitution());
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
                if (isPlayer) {
                    boolean collisionsAllowed = entityCollisions.enabled()
                            && entityCollisions.enabledGamemodes().contains(((Player) entity).getGameMode().name());

                    if (!collisionsAllowed) {
                        state.insideTicks = 0;
                        state.outsideTicks = 0;
                        if (!state.physicsDisabled) {
                            bodyInterface.setIsSensor(state.bodyId, true);
                            state.physicsDisabled = true;
                        }
                        bodyInterface.moveKinematic(state.bodyId, targetPosition, targetRotation, 1.0f / Math.max(1, world.getSettings().tickRate()));
                        return;
                    }

                    if (state.physicsDisabled && !entityCollisions.stuck().enabled()) {
                        bodyInterface.setIsSensor(state.bodyId, false);
                        state.physicsDisabled = false;
                    }

                    boolean deepInside = hasMovingOverlap(insideShapeFor(poseState), targetPosition, targetRotation);
                    if (deepInside) {
                        state.insideTicks++;
                        state.outsideTicks = 0;
                    } else {
                        state.outsideTicks++;
                        state.insideTicks = 0;
                    }

                    if (entityCollisions.stuck().enabled()) {
                        if (!state.physicsDisabled && state.insideTicks >= entityCollisions.stuck().sensorEnableTicks()) {
                            bodyInterface.setIsSensor(state.bodyId, true);
                            state.physicsDisabled = true;
                        } else if (state.physicsDisabled && state.outsideTicks >= entityCollisions.stuck().sensorDisableTicks()) {
                            bodyInterface.setIsSensor(state.bodyId, false);
                            state.physicsDisabled = false;
                        }
                    }

                    float baseDt = 1.0f / Math.max(1, world.getSettings().tickRate());
                    float dt = baseDt;
                    if (!state.physicsDisabled) {
                        if (entityCollisions.pushing().enabled()) {
                            boolean touchingDynamic = hasMovingOverlap(shapeFor(poseState), targetPosition, targetRotation);
                            if (touchingDynamic) {
                                dt = baseDt * entityCollisions.pushing().kinematicContactDtMultiplier();
                            }
                        }
                    }
                    bodyInterface.moveKinematic(state.bodyId, targetPosition, targetRotation, dt);
                } else {
                    bodyInterface.moveKinematic(state.bodyId, targetPosition, targetRotation, 1.0f / Math.max(1, world.getSettings().tickRate()));
                }
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

    private Shape insideShapeFor(PoseState poseState) {
        if (poseState == PoseState.SNEAKING) {
            return sneakingInsideShape;
        }
        if (poseState == PoseState.HORIZONTAL) {
            return horizontalInsideShape;
        }
        return standingInsideShape;
    }

    private boolean hasMovingOverlap(Shape shape, RVec3 position, Quat rotation) {
        CollideShapeSettings settings = new CollideShapeSettings();
        settings.setActiveEdgeMode(EActiveEdgeMode.CollideOnlyWithActive);

        PhysicsSystem physicsSystem = world.getPhysicsSystem();
        try (AnyHitCollideShapeCollector collector = new AnyHitCollideShapeCollector();
             SpecifiedBroadPhaseLayerFilter bpFilter = new SpecifiedBroadPhaseLayerFilter(PhysicsLayers.BP_MOVING);
             SpecifiedObjectLayerFilter objFilter = new SpecifiedObjectLayerFilter(PhysicsLayers.OBJ_MOVING)) {

            physicsSystem.getNarrowPhaseQuery().collideShape(
                    shape,
                    Vec3.sReplicate(1.0f),
                    RMat44.sRotationTranslation(rotation, position),
                    settings,
                    RVec3.sZero(),
                    collector,
                    bpFilter,
                    objFilter
            );

            return collector.hadHit();
        }
    }

    private PoseState resolvePose(LivingEntity entity) {
        if (entity.isGliding() || entity.isSwimming()) {
            return PoseState.HORIZONTAL;
        }
        if (entity instanceof Player player && player.isSneaking()) {
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
        private volatile boolean physicsDisabled;
        private volatile int insideTicks;
        private volatile int outsideTicks;

        private ProxyState(UUID entityId) {
            this.entityId = entityId;
            this.bodyId = -1;
            this.poseState = PoseState.STANDING;
            this.physicsDisabled = false;
            this.insideTicks = 0;
            this.outsideTicks = 0;
        }
    }

    public record CollisionFeedbackDTO(UUID entityId, org.bukkit.util.Vector knockback, float impulse) {
    }
}
