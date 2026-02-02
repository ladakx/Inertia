package com.ladakx.inertia.api.service;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.readonly.RVec3Arg;
import com.ladakx.inertia.common.PhysicsGraphUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.common.utils.ConvertUtils;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.DisplayedPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

public class PhysicsManipulationService {

    public UUID startGrabbing(PhysicsWorld space, AbstractPhysicsBody object, Player player, double force, DoubleSupplier distanceSupplier) {
        Body body = object.getBody();
        return space.addTickTask(() -> {
            if (!object.isValid() || !body.isActive()) {
                space.getBodyInterface().activateBody(body.getId());
            }
            RVec3 physicsVec = body.getPosition();
            double dist = distanceSupplier.getAsDouble();
            
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection();
            Vector target = eye.toVector().add(dir.multiply(dist));
            
            Vector current = ConvertUtils.toBukkit(physicsVec);
            Vector diff = target.subtract(current);
            
            // Simple P-controller for velocity
            body.setLinearVelocity(ConvertUtils.toVec3(diff.multiply(force)));
        });
    }

    public void stopGrabbing(PhysicsWorld space, UUID taskId) {
        if (taskId != null) {
            space.removeTickTask(taskId);
        }
    }

    public void createStaticJoint(PhysicsWorld space, AbstractPhysicsBody object, Location location) {
        RVec3Arg pos = space.toJolt(location);
        Body fixedBody = Body.sFixedToWorld();
        
        SixDofConstraintSettings settings = new SixDofConstraintSettings();
        settings.makeFixedAxis(EAxis.TranslationX);
        settings.makeFixedAxis(EAxis.TranslationY);
        settings.makeFixedAxis(EAxis.TranslationZ);
        settings.setPosition1(pos);
        settings.setPosition2(pos);
        
        TwoBodyConstraint constraint = settings.create(fixedBody, object.getBody());
        space.addConstraint(constraint);
        object.addRelatedConstraint(constraint.toRef());
    }

    public void weldBodies(PhysicsWorld space, AbstractPhysicsBody obj1, AbstractPhysicsBody obj2, boolean keepDistance) {
        Body b1 = obj1.getBody();
        Body b2 = obj2.getBody();
        
        RVec3 pos1 = b1.getPosition();
        RVec3 pos2 = b2.getPosition();

        SixDofConstraintSettings settings = new SixDofConstraintSettings();
        settings.makeFixedAxis(EAxis.TranslationX);
        settings.makeFixedAxis(EAxis.TranslationY);
        settings.makeFixedAxis(EAxis.TranslationZ);
        // Usually welding implies fixing rotation too, but keeping implementation close to original for now
        // If strict welding is needed, rotation axes should also be fixed.
        
        if (keepDistance) {
            settings.setSpace(EConstraintSpace.LocalToBodyCom);
            // Jolt calculates relative offsets automatically when created if not specified in WorldSpace
            // But to be explicit like original code:
             RVec3 p1 = new RVec3(pos1);
             p1.addInPlace(-pos2.x(), -pos2.y(), -pos2.z());
             RVec3 p2 = new RVec3(pos2);
             p2.addInPlace(-pos1.x(), -pos1.y(), -pos1.z());
             settings.setPosition1(p1);
             settings.setPosition2(p2);
        } else {
             settings.setPosition1(pos1);
             settings.setPosition2(pos2);
        }

        TwoBodyConstraint constraint = settings.create(b1, b2);
        space.addConstraint(constraint);
        
        // Link constraint to both objects so destroying either removes it
        obj1.addRelatedConstraint(constraint.toRef());
        obj2.addRelatedConstraint(constraint.toRef());
        
        space.getBodyInterface().activateBody(b1.getId());
        space.getBodyInterface().activateBody(b2.getId());
    }

    public int freezeCluster(PhysicsWorld space, AbstractPhysicsBody root) {
        Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, root);
        UUID clusterId = UUID.randomUUID();
        int count = 0;
        
        for (AbstractPhysicsBody body : cluster) {
            if (body instanceof DisplayedPhysicsBody displayed) {
                try {
                    displayed.freeze(clusterId);
                    count++;
                } catch (Exception e) {
                    InertiaLogger.error("Failed to freeze body part", e);
                }
            } else {
                body.destroy();
            }
        }
        return count;
    }

    public int removeCluster(PhysicsWorld space, AbstractPhysicsBody root) {
        Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, root);
        int count = 0;
        for (AbstractPhysicsBody body : cluster) {
            try {
                body.destroy();
                count++;
            } catch (Exception e) {
                InertiaLogger.error("Failed to remove body in cluster", e);
            }
        }
        return count;
    }
    
    public int removeStaticCluster(Entity startEntity) {
        if (!startEntity.getPersistentDataContainer().has(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING)) {
            startEntity.remove();
            return 1;
        }
        String clusterId = startEntity.getPersistentDataContainer().get(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING);
        // This involves a BFS search in Bukkit world, similar to original implementation
        // Simplified for this context:
        return removeStaticRecursively(startEntity, clusterId, new java.util.HashSet<>());
    }

    private int removeStaticRecursively(Entity current, String targetId, Set<UUID> visited) {
        if (visited.contains(current.getUniqueId())) return 0;
        visited.add(current.getUniqueId());
        
        int count = 1;
        current.remove();
        
        for (Entity neighbor : current.getNearbyEntities(2, 2, 2)) {
             String nId = neighbor.getPersistentDataContainer().get(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING);
             if (targetId.equals(nId)) {
                 count += removeStaticRecursively(neighbor, targetId, visited);
             }
        }
        return count;
    }
}