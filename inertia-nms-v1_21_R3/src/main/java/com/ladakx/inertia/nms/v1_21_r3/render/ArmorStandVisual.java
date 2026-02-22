package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r3.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class ArmorStandVisual extends AbstractSimpleEntityVisual {

    private final ItemModelResolver itemResolver;

    public ArmorStandVisual(RenderEntityDefinition definition, PacketFactory packetFactory, ItemModelResolver itemResolver) {
        super(definition, packetFactory);
        this.itemResolver = itemResolver;
    }

    @Override
    public Object createSpawnPacket(org.bukkit.Location location, Quaternionf rotation) {
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

        Object equipmentPacket = createEquipmentPacket();
        if (equipmentPacket == null) {
            return packetFactory.createBundlePacket(List.of(spawnPacket, metaPacket));
        }
        return packetFactory.createBundlePacket(List.of(spawnPacket, metaPacket, equipmentPacket));
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        if (MetadataAccessors.ARMOR_STAND_CLIENT_FLAGS == null) return;
        byte flags = 0;
        if (definition.small()) flags |= 0x01;
        if (definition.arms()) flags |= 0x04;
        if (!definition.basePlate()) flags |= 0x08;
        if (definition.marker()) flags |= 0x10;
        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.ARMOR_STAND_CLIENT_FLAGS, flags));
    }

    private Object createEquipmentPacket() {
        ItemStack bukkitItem = null;

        if (definition.itemModelKey() != null) {
            bukkitItem = itemResolver != null ? itemResolver.resolve(definition.itemModelKey()) : null;
        } else if (definition.blockType() != null) {
            bukkitItem = new ItemStack(definition.blockType());
        }

        if (bukkitItem == null) {
            Boolean sendBarrier = RenderSettings.getBoolean(definition.settings(), "armorstand.fallback-barrier");
            if (sendBarrier != null && sendBarrier) {
                bukkitItem = new ItemStack(Material.BARRIER);
            }
        }

        if (bukkitItem == null) return null;

        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(bukkitItem);
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = List.of(Pair.of(EquipmentSlot.HEAD, nms));
        return new ClientboundSetEquipmentPacket(entityId, slots);
    }
}

