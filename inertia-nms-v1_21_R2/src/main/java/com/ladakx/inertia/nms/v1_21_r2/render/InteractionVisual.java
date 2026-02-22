package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r2.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.RenderSettings;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

public final class InteractionVisual extends AbstractSimpleEntityVisual {

    public InteractionVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        super(definition, packetFactory);
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        Float width = RenderSettings.getFloat(definition.settings(), "interaction.width");
        if (width == null) width = RenderSettings.getFloat(definition.settings(), "width");
        if (width != null && MetadataAccessors.INTERACTION_WIDTH != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.INTERACTION_WIDTH, width));
        }

        Float height = RenderSettings.getFloat(definition.settings(), "interaction.height");
        if (height == null) height = RenderSettings.getFloat(definition.settings(), "height");
        if (height != null && MetadataAccessors.INTERACTION_HEIGHT != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.INTERACTION_HEIGHT, height));
        }

        Boolean responsive = RenderSettings.getBoolean(definition.settings(), "interaction.responsive");
        if (responsive == null) responsive = RenderSettings.getBoolean(definition.settings(), "responsive");
        if (responsive != null && MetadataAccessors.INTERACTION_RESPONSIVE != null) {
            data.add(SynchedEntityData.DataValue.create(MetadataAccessors.INTERACTION_RESPONSIVE, responsive));
        }
    }
}

