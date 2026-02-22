package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r2.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.EntityIdProvider;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Location;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractSimpleEntityVisual implements NetworkVisual {

    protected final int entityId;
    protected final UUID uuid;
    protected final RenderEntityDefinition definition;
    protected final PacketFactory packetFactory;
    protected boolean isGlowing = false;

    protected AbstractSimpleEntityVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        this.entityId = EntityIdProvider.getInstance().getNextId();
        this.uuid = UUID.randomUUID();
        this.definition = definition;
        this.packetFactory = packetFactory;
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
    public Object createSpawnPacket(Location location, Quaternionf rotation) {
        Object spawnPacket = packetFactory.createSpawnPacket(
                definition.kind(),
                entityId,
                uuid,
                location.getX(), location.getY(), location.getZ(),
                yawFrom(rotation),
                pitchFrom(rotation)
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        addTypeSpecificMetadata(metadata);

        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        return packetFactory.createBundlePacket(List.of(spawnPacket, metaPacket));
    }

    @Override
    public Object createDestroyPacket() {
        return packetFactory.createDestroyPacket(entityId);
    }

    @Override
    public Object createTeleportPacket(Location location, Quaternionf rotation) {
        return createPositionPacket(location, rotation);
    }

    @Override
    public Object createPositionPacket(Location location, Quaternionf rotation) {
        return packetFactory.createTeleportPacket(
                entityId,
                location.getX(),
                location.getY(),
                location.getZ(),
                yawFrom(rotation),
                pitchFrom(rotation),
                false
        );
    }

    @Override
    public Object createTransformMetadataPacket(Quaternionf rotation) {
        return null;
    }

    @Override
    public Object createMetadataPacket() {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        addTypeSpecificMetadata(metadata);
        return packetFactory.createMetaPacket(entityId, metadata);
    }

    protected void addBaseMetadata(List<SynchedEntityData.DataValue<?>> data) {
        byte flags = 0;
        if (definition.invisible()) flags |= 0x20;
        if (isGlowing) flags |= 0x40;
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ENTITY_FLAGS, flags));

        Boolean silent = RenderSettings.getBoolean(definition.settings(), "silent");
        if (silent != null && MetadataAccessors.ENTITY_SILENT != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ENTITY_SILENT, silent));
        }

        Boolean noGravity = RenderSettings.getBoolean(definition.settings(), "no-gravity");
        if (noGravity != null && MetadataAccessors.ENTITY_NO_GRAVITY != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ENTITY_NO_GRAVITY, noGravity));
        }
    }

    protected abstract void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data);

    protected float yawFrom(Quaternionf q) {
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float fx = 2.0f * (x * z + w * y);
        float fz = 1.0f - 2.0f * (x * x + y * y);
        double yaw = Math.atan2(-fx, fz);
        return (float) Math.toDegrees(yaw);
    }

    protected float pitchFrom(Quaternionf q) {
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float fy = 2.0f * (y * z - w * x);
        if (fy > 1.0f) fy = 1.0f;
        if (fy < -1.0f) fy = -1.0f;
        double pitch = Math.asin(-fy);
        return (float) Math.toDegrees(pitch);
    }
}

