package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r3.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.EntityIdProvider;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.enums.InertiaBillboard;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractNetworkVisual implements NetworkVisual {

    protected final int entityId;
    protected final UUID uuid;
    protected final RenderEntityDefinition definition;
    protected final PacketFactory packetFactory;
    protected boolean isGlowing = false;

    // Mutable (runtime) overrides. Defaults are initialized from the definition.
    protected boolean invisibleFlag;
    protected @Nullable InertiaBillboard billboardMode;
    protected @Nullable Float viewRange;
    protected @Nullable Float shadowRadius;
    protected @Nullable Float shadowStrength;
    protected @Nullable Integer interpolationDuration;
    protected @Nullable Integer teleportDuration;

    protected final Vector3f scale = new Vector3f();
    protected final Vector3f translation = new Vector3f();
    protected final Vector3f localOffset = new Vector3f();
    protected final Quaternionf rightRotation = new Quaternionf();
    protected boolean rotateTranslation;

    protected AbstractNetworkVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        this.entityId = EntityIdProvider.getInstance().getNextId();
        this.uuid = UUID.randomUUID();
        this.definition = definition;
        this.packetFactory = packetFactory;

        this.invisibleFlag = definition.invisible();
        this.billboardMode = definition.billboard();
        this.viewRange = definition.viewRange();
        this.shadowRadius = definition.shadowRadius();
        this.shadowStrength = definition.shadowStrength();
        this.interpolationDuration = definition.interpolationDuration();
        this.teleportDuration = definition.teleportDuration();

        org.bukkit.util.Vector defScale = definition.scale();
        this.scale.set((float) defScale.getX(), (float) defScale.getY(), (float) defScale.getZ());
        org.bukkit.util.Vector defTranslation = definition.translation();
        this.translation.set((float) defTranslation.getX(), (float) defTranslation.getY(), (float) defTranslation.getZ());
        org.bukkit.util.Vector defLocalOffset = definition.localOffset();
        this.localOffset.set((float) defLocalOffset.getX(), (float) defLocalOffset.getY(), (float) defLocalOffset.getZ());
        this.rightRotation.set(definition.localRotation());
        this.rotateTranslation = definition.rotTranslation();
    }

    @Override
    public int getId() {
        return entityId;
    }

    @Override
    public void setGlowing(boolean glowing) {
        this.isGlowing = glowing;
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisibleFlag = invisible;
    }

    @Override
    public void setScale(@Nullable Vector scale) {
        Vector v = (scale != null) ? scale : definition.scale();
        this.scale.set((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }

    @Override
    public void setTranslation(@Nullable Vector translation) {
        Vector v = (translation != null) ? translation : definition.translation();
        this.translation.set((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }

    @Override
    public void setRightRotation(@Nullable Quaternionf rotation) {
        Quaternionf q = (rotation != null) ? rotation : definition.localRotation();
        this.rightRotation.set(q);
    }

    @Override
    public void setBillboard(@Nullable InertiaBillboard billboard) {
        this.billboardMode = billboard;
    }

    @Override
    public void setViewRange(@Nullable Float viewRange) {
        this.viewRange = viewRange;
    }

    @Override
    public void setShadowRadius(@Nullable Float shadowRadius) {
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void setShadowStrength(@Nullable Float shadowStrength) {
        this.shadowStrength = shadowStrength;
    }

    @Override
    public void setInterpolationDuration(@Nullable Integer interpolationDuration) {
        this.interpolationDuration = interpolationDuration;
    }

    @Override
    public void setTeleportDuration(@Nullable Integer teleportDuration) {
        this.teleportDuration = teleportDuration;
    }

    @Override
    public Object createSpawnPacket(Location location, Quaternionf rotation) {
        Object spawnPacket = packetFactory.createSpawnPacket(
                definition.kind(),
                entityId,
                uuid,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        addTypeSpecificMetadata(metadata);

        addTransformationMetadata(metadata, rotation);

        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);

        return packetFactory.createBundlePacket(List.of(spawnPacket, metaPacket));
    }
    @Override
    public Object createDestroyPacket() {
        return packetFactory.createDestroyPacket(entityId);
    }

    @Override
    public Object createTeleportPacket(Location location, Quaternionf rotation) {
        Object positionPacket = createPositionPacket(location, rotation);
        Object transformPacket = createTransformMetadataPacket(rotation);

        if (transformPacket == null) {
            return positionPacket;
        }
        return packetFactory.createBundlePacket(List.of(positionPacket, transformPacket));
    }

    @Override
    public Object createPositionPacket(Location location, Quaternionf rotation) {
        return packetFactory.createTeleportPacket(
                entityId,
                location.getX(),
                location.getY(),
                location.getZ(),
                0, 0,
                false
        );
    }

    @Override
    public Object createTransformMetadataPacket(Quaternionf rotation) {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addTransformationMetadata(metadata, rotation);
        if (metadata.isEmpty()) {
            return null;
        }
        return packetFactory.createMetaPacket(entityId, metadata);
    }

    @Override
    public Object createMetadataPacket() {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        // Keep generic metadata updates separate from transformation metadata to avoid duplicates
        // when spawn/forced-resync already sent transform values.
        addTypeSpecificMetadata(metadata);
        return packetFactory.createMetaPacket(entityId, metadata);
    }

    protected abstract void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data);

    protected abstract Vector3f calculateTranslation(Vector3f scale, Quaternionf rotation);

    protected Vector3f translation() {
        return translation;
    }

    protected Vector3f localOffset() {
        return localOffset;
    }

    protected boolean rotateTranslation() {
        return rotateTranslation;
    }

    protected void addBaseMetadata(List<SynchedEntityData.DataValue<?>> data) {
        byte flags = 0;
        if (invisibleFlag) flags |= 0x20;
        if (isGlowing) flags |= 0x40;
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ENTITY_FLAGS, flags));

        byte billboard = 0;
        if (billboardMode != null) {
            billboard = (byte) billboardMode.ordinal();
        }
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_BILLBOARD, billboard));

        if (viewRange != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_VIEW_RANGE, viewRange));
        }
        if (shadowRadius != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SHADOW_RADIUS, shadowRadius));
        }
        if (shadowStrength != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SHADOW_STRENGTH, shadowStrength));
        }
    }

    protected void addTransformationMetadata(List<SynchedEntityData.DataValue<?>> data, Quaternionf rotation) {
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SCALE, scale));
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_DELAY_INTERPOLATION_DURATION, -1));
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_LEFT_ROTATION, rotation));

        Vector3f translation = calculateTranslation(scale, rotation);
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_TRANSLATION, translation));

        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_RIGHT_ROTATION, rightRotation));

        if (interpolationDuration != null && interpolationDuration > 0) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_INTERPOLATION_DURATION, interpolationDuration));
        }
        if (teleportDuration != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_TELEPORT_DURATION, teleportDuration));
        }
    }
}
