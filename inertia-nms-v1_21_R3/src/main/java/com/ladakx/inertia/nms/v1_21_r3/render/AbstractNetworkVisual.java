package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r3.utils.MetadataAccessors;
import com.ladakx.inertia.nms.v1_21_r3.utils.PacketUtils;
import com.ladakx.inertia.rendering.EntityIdProvider;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractNetworkVisual implements NetworkVisual {
    protected final int entityId;
    protected final UUID uuid;
    protected final RenderEntityDefinition definition;
    protected final PacketFactory packetFactory;

    protected boolean isGlowing = false;

    protected AbstractNetworkVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
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
        if (this.isGlowing != glowing) {
            this.isGlowing = glowing;
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateMetadataFor(p);
            }
        }
    }

    @Override
    public void spawnFor(Player player) {
        Location loc = player.getLocation();
        Object spawnPacket = packetFactory.createSpawnPacket(
                definition.kind(),
                entityId,
                uuid,
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        addTypeSpecificMetadata(metadata);
        addTransformationMetadata(metadata, new Quaternionf());

        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        PacketUtils.send(player, List.of(spawnPacket, metaPacket));
    }

    @Override
    public void destroyFor(Player player) {
        Object destroyPacket = packetFactory.createDestroyPacket(entityId);
        PacketUtils.send(player, (net.minecraft.network.protocol.Packet<?>) destroyPacket);
    }

    @Override
    public void updatePositionFor(Player player, Location location, Quaternionf rotation) {
        Object teleportPacket = packetFactory.createTeleportPacket(
                entityId,
                location.getX(),
                location.getY(),
                location.getZ(),
                0, 0,
                false
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addTransformationMetadata(metadata, rotation);

        if (!metadata.isEmpty()) {
            Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
            PacketUtils.send(player, List.of(teleportPacket, metaPacket));
        } else {
            PacketUtils.send(player, (net.minecraft.network.protocol.Packet<?>) teleportPacket);
        }
    }

    @Override
    public void updateMetadataFor(Player player) {
        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        PacketUtils.send(player, (net.minecraft.network.protocol.Packet<?>) metaPacket);
    }

    protected abstract void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data);

    // Абстрактный метод для специфичной логики смещения (Block vs Item)
    protected abstract Vector3f calculateTranslation(Vector3f scale, Quaternionf rotation);

    protected void addBaseMetadata(List<SynchedEntityData.DataValue<?>> data) {
        byte flags = 0;
        if (definition.invisible()) flags |= 0x20;
        if (isGlowing) flags |= 0x40;

        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ENTITY_FLAGS, flags));

        byte billboard = 0;
        if (definition.billboard() != null) {
            billboard = (byte) definition.billboard().ordinal();
        }
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_BILLBOARD, billboard));

        if (definition.viewRange() != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_VIEW_RANGE, definition.viewRange()));
        }
        if (definition.shadowRadius() != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SHADOW_RADIUS, definition.shadowRadius()));
        }
        if (definition.shadowStrength() != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SHADOW_STRENGTH, definition.shadowStrength()));
        }
    }

    protected void addTransformationMetadata(List<SynchedEntityData.DataValue<?>> data, Quaternionf rotation) {
        // 1. Scale
        org.bukkit.util.Vector scale = definition.scale();
        Vector3f scaleVec = new Vector3f((float)scale.getX(), (float)scale.getY(), (float)scale.getZ());
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_SCALE, scaleVec));

        // 2. Left Rotation
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_LEFT_ROTATION, rotation));

        // 3. Translation (Полиморфный вызов)
        Vector3f translation = calculateTranslation(scaleVec, rotation);
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_TRANSLATION, translation));

        // 4. Right Rotation
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_RIGHT_ROTATION, definition.localRotation()));

        // Интерполяция
        if (definition.interpolationDuration() != null && definition.interpolationDuration() > 0) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_INTERPOLATION_DURATION, definition.interpolationDuration()));
        }
        if (definition.teleportDuration() != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.DISPLAY_TELEPORT_DURATION, definition.teleportDuration()));
        }
    }
}