package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r2.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.Locale;

public final class ShulkerVisual extends AbstractSimpleEntityVisual {

    public ShulkerVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        super(definition, packetFactory);
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        Integer peekInt = RenderSettings.getInt(definition.settings(), "shulker.peek");
        if (peekInt == null) peekInt = RenderSettings.getInt(definition.settings(), "peek");
        if (peekInt != null && MetadataAccessors.SHULKER_PEEK != null) {
            int clamped = Math.max(0, Math.min(100, peekInt));
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.SHULKER_PEEK, (byte) clamped));
        }

        Byte color = null;
        Integer colorId = RenderSettings.getInt(definition.settings(), "shulker.color-id");
        if (colorId == null) colorId = RenderSettings.getInt(definition.settings(), "color-id");
        if (colorId != null) {
            int clamped = Math.max(-1, Math.min(15, colorId));
            color = (byte) clamped;
        } else {
            String colorName = RenderSettings.getString(definition.settings(), "shulker.color");
            if (colorName == null) colorName = RenderSettings.getString(definition.settings(), "color");
            if (colorName != null && !colorName.isBlank()) {
                try {
                    DyeColor dye = DyeColor.valueOf(colorName.trim().toUpperCase(Locale.ROOT));
                    color = (byte) dye.getId();
                } catch (Throwable ignored) {
                }
            }
        }

        if (color != null && MetadataAccessors.SHULKER_COLOR != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.SHULKER_COLOR, color));
        }
    }
}

