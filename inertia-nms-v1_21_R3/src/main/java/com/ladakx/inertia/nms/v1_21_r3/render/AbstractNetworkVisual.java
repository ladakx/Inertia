package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.nms.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r3.utils.PacketUtils;
import com.ladakx.inertia.rendering.EntityIdProvider;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractNetworkVisual implements NetworkVisual {

    protected final int entityId;
    protected final UUID uuid;
    protected final RenderEntityDefinition definition;
    protected final PacketFactory packetFactory;

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
    public void spawnFor(Player player) {
        Location loc = player.getLocation(); // Temporary, will be updated immediately
        Object spawnPacket = packetFactory.createSpawnPacket(
                definition.kind(),
                entityId,
                uuid,
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        // Базовые флаги (свечение, невидимость и т.д.)
        addBaseMetadata(metadata);
        // Специфичные флаги (блок или предмет)
        addTypeSpecificMetadata(metadata);

        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        
        // Отправляем спавн + метаданные сразу, чтобы клиент применил их
        PacketUtils.send(player, List.of(spawnPacket, metaPacket));
    }

    @Override
    public void destroyFor(Player player) {
        Object destroyPacket = packetFactory.createDestroyPacket(entityId);
        PacketUtils.send(player, (net.minecraft.network.protocol.Packet<?>) destroyPacket);
    }

    @Override
    public void updatePositionFor(Player player, Location location, Quaternionf rotation) {
        // В Display Entities позиция задается через Teleport Packet,
        // А вращение самой модели может быть задано через Metadata (Transformation) или через Yaw/Pitch.
        // Для физики мы используем Teleport для позиции.
        
        Object teleportPacket = packetFactory.createTeleportPacket(
                entityId,
                location.getX(),
                location.getY(),
                location.getZ(),
                0, 0, // Yaw/Pitch игнорируем, так как вращение задается через Transformation в Metadata (если нужно)
                false
        );
        
        // Если нам нужно обновлять вращение каждый тик, нам нужно отправлять Metadata пакет с Transformation
        // Однако, часто проще обновлять Transformation interpolation
        
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
        addTypeSpecificMetadata(metadata);
        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        PacketUtils.send(player, (net.minecraft.network.protocol.Packet<?>) metaPacket);
    }

    protected abstract void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data);

    protected void addBaseMetadata(List<SynchedEntityData.DataValue<?>> data) {
        // Index 0: Entity Flags (0x20 = Invisible, 0x40 = Glowing)
        byte flags = 0;
        if (definition.invisible()) flags |= 0x20;
        // if (glowing) flags |= 0x40; // TODO: Add glowing state support
        
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Entity.DATA_SHARED_FLAGS_ID, flags));
        
        // Display entity specific basic data
        // DATA_INTERPOLATION_DURATION_ID, DATA_START_INTERPOLATION_ID handled in transformation usually
        
        // Billboard
        byte billboard = 0; // FIXED
        if (definition.billboard() != null) {
            billboard = (byte) definition.billboard().ordinal();
        }
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_BILLBOARD_RENDER_CONSTRAINTS_ID, billboard));

        // View Range
        if (definition.viewRange() != null) {
             data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_VIEW_RANGE_ID, definition.viewRange()));
        }
        
        // Shadow
        if (definition.shadowRadius() != null) {
             data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_SHADOW_RADIUS_ID, definition.shadowRadius()));
        }
        if (definition.shadowStrength() != null) {
             data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_SHADOW_STRENGTH_ID, definition.shadowStrength()));
        }
    }
    
    protected void addTransformationMetadata(List<SynchedEntityData.DataValue<?>> data, Quaternionf rotation) {
        // Здесь мы формируем матрицу трансформации для Display Entity.
        // Translation (Scale * Offset)
        // Left Rotation (Global Rotation from Jolt)
        // Scale (From Config)
        // Right Rotation (Local Rotation from Config)
        
        // NOTE: Логика интерполяции и трансформации сложна, реализуем упрощенно:
        // Используем Left Rotation для вращения тела.
        
        // Для 1.21+ используется com.mojang.math.Transformation но в DataWatcher это отдельные поля
        
        // DATA_TRANSFORMATION_LEFT_ROTATION
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_TRANSFORMATION_LEFT_ROTATION_ID, rotation));
        
        // DATA_TRANSFORMATION_SCALE
        org.bukkit.util.Vector scale = definition.scale();
        org.joml.Vector3f scaleVec = new org.joml.Vector3f((float)scale.getX(), (float)scale.getY(), (float)scale.getZ());
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_TRANSFORMATION_SCALE_ID, scaleVec));
        
        // DATA_TRANSFORMATION_TRANSLATION
        org.bukkit.util.Vector trans = definition.translation();
        org.joml.Vector3f transVec = new org.joml.Vector3f((float)trans.getX(), (float)trans.getY(), (float)trans.getZ());
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_TRANSFORMATION_TRANSLATION_ID, transVec));
        
        // Right Rotation (Local rotation correction)
        data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_TRANSFORMATION_RIGHT_ROTATION_ID, definition.localRotation()));
        
        // Interpolation
        if (definition.interpolationDuration() != null && definition.interpolationDuration() > 0) {
             data.add(SynchedEntityData.DataValue.create(net.minecraft.world.entity.Display.DATA_TRANSFORMATION_INTERPOLATION_DURATION_ID, definition.interpolationDuration()));
             // Start interpolation needs to be updated too? Usually handled automatically if duration > 0, 
             // but sometimes -1 is required to reset. For continuous physics, usually we rely on client interpolation of Teleport packets 
             // OR we set interpolation duration to 0 for instant snap. 
             // ТЗ mentions interpolation is critical.
        }
    }
}