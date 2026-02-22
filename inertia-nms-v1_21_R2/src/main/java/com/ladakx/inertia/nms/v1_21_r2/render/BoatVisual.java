package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BoatVisual extends AbstractSimpleEntityVisual {

    public BoatVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        super(definition, packetFactory);
    }

    @Override
    public Object createSpawnPacket(Location location, Quaternionf rotation) {
        EntityType<?> type = resolveBoatEntityType();

        Object spawnPacket = new ClientboundAddEntityPacket(
                entityId,
                uuid,
                location.getX(), location.getY(), location.getZ(),
                pitchFrom(rotation),
                yawFrom(rotation),
                type,
                0,
                Vec3.ZERO,
                yawFrom(rotation)
        );

        List<SynchedEntityData.DataValue<?>> metadata = new ArrayList<>();
        addBaseMetadata(metadata);
        addTypeSpecificMetadata(metadata);

        Object metaPacket = packetFactory.createMetaPacket(entityId, metadata);
        return packetFactory.createBundlePacket(List.of(spawnPacket, metaPacket));
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        // Boat variant is encoded in the spawned EntityType (e.g. OAK_BOAT / OAK_CHEST_BOAT).
    }

    private EntityType<?> resolveBoatEntityType() {
        String wood = RenderSettings.getString(definition.settings(), "boat.type");
        if (wood == null) wood = RenderSettings.getString(definition.settings(), "type");
        if (wood == null || wood.isBlank()) wood = "OAK";

        Boolean chest = RenderSettings.getBoolean(definition.settings(), "boat.chest");
        if (chest == null) chest = RenderSettings.getBoolean(definition.settings(), "chest");
        boolean isChest = chest != null && chest;

        String normalized = wood.trim().toUpperCase(Locale.ROOT);
        String fieldName = normalized + (isChest ? "_CHEST_BOAT" : "_BOAT");
        EntityType<?> resolved = resolveEntityTypeConstant(fieldName);
        if (resolved != null) return resolved;

        EntityType<?> fallback = resolveEntityTypeConstant("OAK_BOAT");
        return fallback != null ? fallback : EntityType.ARMOR_STAND;
    }

    private EntityType<?> resolveEntityTypeConstant(String staticFieldName) {
        if (staticFieldName == null || staticFieldName.isBlank()) return null;
        try {
            Field f = EntityType.class.getField(staticFieldName);
            Object v = f.get(null);
            if (v instanceof EntityType<?> t) return t;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
