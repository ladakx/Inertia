package com.ladakx.inertia.physics.persistence.storage;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.api.body.MotionType;
import com.ladakx.inertia.api.body.type.IChain;
import com.ladakx.inertia.api.body.type.IRagdoll;
import com.ladakx.inertia.physics.body.InertiaPhysicsBody;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.body.impl.ChainPhysicsBody;
import com.ladakx.inertia.physics.body.impl.RagdollPhysicsBody;
import com.ladakx.inertia.physics.body.impl.TNTPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Location;

import java.util.*;

public final class DynamicBodyStorage {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public DynamicBodyStorage(PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = Objects.requireNonNull(physicsWorldRegistry, "physicsWorldRegistry");
    }

    public List<DynamicBodyStorageRecord> snapshot() {
        Collection<PhysicsWorld> spaces = physicsWorldRegistry.getAllWorlds();
        List<DynamicBodyStorageRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (PhysicsWorld space : spaces) {
            String worldName = space.getBukkitWorld().getName();
            Set<UUID> processedClusters = new HashSet<>();

            for (InertiaPhysicsBody body : space.getBodies()) {
                if (body.getMotionType() == MotionType.STATIC || !body.isValid()) continue;
                if (!(body instanceof AbstractPhysicsBody absBody)) continue;

                UUID clusterId = absBody.getClusterId();
                if (!processedClusters.add(clusterId)) continue; // Already processed this cluster

                // Collect all bodies in this cluster
                List<AbstractPhysicsBody> clusterBodies = new ArrayList<>();
                for (InertiaPhysicsBody b : space.getBodies()) {
                    if (b instanceof AbstractPhysicsBody ab && clusterId.equals(ab.getClusterId()) && ab.isValid()) {
                        clusterBodies.add(ab);
                    }
                }

                if (clusterBodies.isEmpty()) continue;

                // Determine root body
                AbstractPhysicsBody root = clusterBodies.get(0);
                for (AbstractPhysicsBody b : clusterBodies) {
                    if (b instanceof IChain c && c.getLinkIndex() == 0) root = b;
                    else if (b instanceof IRagdoll r && r.getParentPart() == null) root = b;
                }

                // Build custom data
                Map<String, String> customData = new HashMap<>();
                if (root instanceof ChainPhysicsBody chainRoot) {
                    customData.put("size", String.valueOf(chainRoot.getChainLength()));
                }
                if (root instanceof RagdollPhysicsBody ragdollRoot && ragdollRoot.getSkinNickname() != null) {
                    customData.put("skin", ragdollRoot.getSkinNickname());
                }
                if (root instanceof TNTPhysicsBody tntRoot) {
                    customData.put("force", String.valueOf(tntRoot.getExplosionForce()));
                    customData.put("fuse", String.valueOf(tntRoot.getFuseTicks()));
                }

                List<PartState> parts = new ArrayList<>();
                for (AbstractPhysicsBody part : clusterBodies) {
                    RVec3 pos = part.getBody().getPosition();
                    Quat rot = part.getBody().getRotation();
                    com.github.stephengold.joltjni.Vec3 lv = space.getBodyInterface().getLinearVelocity(part.getBody().getId());
                    com.github.stephengold.joltjni.Vec3 av = space.getBodyInterface().getAngularVelocity(part.getBody().getId());

                    boolean anchored = false;
                    double anchorX = 0, anchorY = 0, anchorZ = 0;
                    if (part instanceof ChainPhysicsBody c && c.isAnchored()) {
                        anchored = true;
                        RVec3 anchor = part.getWorldAnchor();
                        if (anchor != null) {
                            anchorX = anchor.xx(); anchorY = anchor.yy(); anchorZ = anchor.zz();
                        }
                    }

                    parts.add(new PartState(
                            part.getPartKey(),
                            pos.xx(), pos.yy(), pos.zz(),
                            rot.getX(), rot.getY(), rot.getZ(), rot.getW(),
                            lv.getX(), lv.getY(), lv.getZ(),
                            av.getX(), av.getY(), av.getZ(),
                            anchored, anchorX, anchorY, anchorZ
                    ));
                }

                Location rootLoc = root.getLocation();
                records.add(new DynamicBodyStorageRecord(
                        clusterId, worldName, root.getBodyId(), root.getMotionType(),
                        root.getFriction(), root.getRestitution(), root.getGravityFactor(),
                        rootLoc.getBlockX() >> 4, rootLoc.getBlockZ() >> 4,
                        now, customData, parts
                ));
            }
        }
        return records;
    }
}
