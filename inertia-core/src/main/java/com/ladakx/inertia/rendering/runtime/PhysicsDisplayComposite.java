package com.ladakx.inertia.rendering.runtime;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.snapshot.SnapshotPool;
import com.ladakx.inertia.physics.world.snapshot.VisualState;
import com.ladakx.inertia.rendering.NetworkEntityTracker;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderModelDefinition;
import com.ladakx.inertia.rendering.staticent.StaticEntityMetadata;
import com.ladakx.inertia.rendering.staticent.StaticEntityPersister;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PhysicsDisplayComposite {

    public record DisplayPart(
            RenderEntityDefinition definition,
            NetworkVisual visual
    ) {
        public DisplayPart {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(visual);
        }
    }

    private final AbstractPhysicsBody owner;
    private final Body body;
    private final RenderModelDefinition model;
    private final List<DisplayPart> parts;
    private final World world;
    private final @Nullable NetworkEntityTracker tracker;
    private final StaticEntityPersister staticEntityPersister;

    private final Vector3f cacheBodyPos = new Vector3f();
    private final Quaternionf cacheBodyRot = new Quaternionf();
    private final Vector3f cacheCenterOffset = new Vector3f();
    private final Vector3f cacheFinalPos = new Vector3f();
    private final Vector3f cacheLocalOffset = new Vector3f();
    private final Quaternionf cacheFinalRot = new Quaternionf();

    public PhysicsDisplayComposite(AbstractPhysicsBody owner,
                                   RenderModelDefinition model,
                                   World world,
                                   List<DisplayPart> parts,
                                   @Nullable NetworkEntityTracker tracker,
                                   StaticEntityPersister staticEntityPersister) {
        this.owner = Objects.requireNonNull(owner);
        this.body = owner.getBody();
        this.model = Objects.requireNonNull(model);
        this.world = Objects.requireNonNull(world);
        this.parts = Collections.unmodifiableList(parts);
        this.tracker = tracker;
        this.staticEntityPersister = Objects.requireNonNull(staticEntityPersister, "staticEntityPersister");
        registerAll();
    }

    private void registerAll() {
        if (tracker == null) return;

        RVec3 pos = body.getPosition();
        Quat rot = body.getRotation();
        RVec3 origin = owner.getSpace().getOrigin();

        Location baseLoc = new Location(world, pos.xx() + origin.xx(), pos.yy() + origin.yy(), pos.zz() + origin.zz());
        Quaternionf baseRot = new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());

        for (DisplayPart part : parts) {
            int visualId = part.visual().getId();
            tracker.register(part.visual(), baseLoc, baseRot);
            owner.getSpace().registerNetworkEntityId(owner, visualId);
        }
    }

    public void capture(boolean sleeping, RVec3 origin, List<VisualState> accumulator, SnapshotPool pool) {
        if (parts.isEmpty()) return;

        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());
        cacheCenterOffset.set(0, 0, 0);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            NetworkVisual visual = part.visual();

            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            if (!visible) continue;

            cacheFinalPos.set(cacheBodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                cacheBodyRot.transform(cacheLocalOffset);
                cacheFinalPos.add(cacheLocalOffset);
            }

            if (model.syncRotation()) {
                cacheFinalRot.set(cacheBodyRot).mul(def.localRotation());
            } else {
                cacheFinalRot.set(def.localRotation());
            }

            VisualState state = pool.borrowState();
            state.set(
                    visual,
                    cacheFinalPos,
                    cacheFinalRot,
                    cacheCenterOffset,
                    def.rotTranslation(),
                    visible
            );
            accumulator.add(state);
        }
    }

    public void setGlowing(boolean glowing) {
        for (DisplayPart part : parts) {
            part.visual().setGlowing(glowing);
            if (tracker != null) {
                tracker.updateMetadata(part.visual());
            }
        }
    }

    public void markAsStatic(@Nullable UUID clusterId) {
        if (parts.isEmpty()) return;

        boolean sleeping = !body.isActive();
        RVec3 origin = owner.getSpace().getOrigin();
        RVec3 bodyPosJolt = body.getPosition();
        Quat bodyRotJolt = body.getRotation();

        cacheBodyPos.set(
                (float) (bodyPosJolt.xx() + origin.xx()),
                (float) (bodyPosJolt.yy() + origin.yy()),
                (float) (bodyPosJolt.zz() + origin.zz())
        );
        cacheBodyRot.set(bodyRotJolt.getX(), bodyRotJolt.getY(), bodyRotJolt.getZ(), bodyRotJolt.getW());

        Location spawnLoc = new Location(world, 0, 0, 0);

        for (DisplayPart part : parts) {
            RenderEntityDefinition def = part.definition();
            NetworkVisual visual = part.visual();

            boolean visible = sleeping ? def.showWhenSleeping() : def.showWhenActive();
            if (!visible) {
                cleanupNetworkVisual(visual);
                continue;
            }

            cacheFinalPos.set(cacheBodyPos);

            if (model.syncPosition()) {
                Vector offset = def.localOffset();
                cacheLocalOffset.set((float) offset.getX(), (float) offset.getY(), (float) offset.getZ());
                cacheBodyRot.transform(cacheLocalOffset);
                cacheFinalPos.add(cacheLocalOffset);
            }

            if (model.syncRotation()) {
                cacheFinalRot.set(cacheBodyRot).mul(def.localRotation());
            } else {
                cacheFinalRot.set(def.localRotation());
            }

            spawnLoc.setX(cacheFinalPos.x);
            spawnLoc.setY(cacheFinalPos.y);
            spawnLoc.setZ(cacheFinalPos.z);

            try {
                StaticEntityMetadata metadata = new StaticEntityMetadata(
                        owner.getBodyId(),
                        owner.getUuid(),
                        model.id(),
                        def.key(),
                        clusterId
                );
                staticEntityPersister.persist(spawnLoc, def, cacheFinalRot, metadata);
            } finally {
                cleanupNetworkVisual(visual);
            }
        }
    }

    public boolean isValid() {
        return body != null && !parts.isEmpty();
    }

    public void destroy() {
        if (tracker != null) {
            for (DisplayPart part : parts) {
                int visualId = part.visual().getId();
                // Tracker handles packet buffering for removal
                tracker.unregister(part.visual());

                if (owner.isValid()) {
                    owner.getSpace().unregisterNetworkEntityId(visualId);
                }
            }
        }
    }

    private void cleanupNetworkVisual(NetworkVisual visual) {
        if (tracker != null) {
            // Tracker handles packet buffering for removal
            tracker.unregister(visual);
        }
        try {
            owner.getSpace().unregisterNetworkEntityId(visual.getId());
        } catch (Exception ignored) {
        }
    }
}