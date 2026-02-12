package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r2.utils.MetadataAccessors;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class BlockDisplayVisual extends AbstractNetworkVisual {

    public BlockDisplayVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        super(definition, packetFactory);
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        Material material = definition.blockType();
        if (material == null) material = Material.STONE;

        CraftBlockData cbd = (CraftBlockData) material.createBlockData();
        BlockState blockState = cbd.getState();

        data.add(SynchedEntityData.DataValue.create(MetadataAccessors.BLOCK_DISPLAY_STATE, blockState));
    }

    @Override
    protected Vector3f calculateTranslation(Vector3f scale, Quaternionf rotation) {
        // 1. Center (-0.5 с учетом масштаба)
        Vector3f center = new Vector3f(-0.5f, -0.5f, -0.5f).mul(scale);

        // 2. originalTrans (Translation + LocalOffset из конфига)
        Vector3f originalTrans = new Vector3f(
                (float) definition.translation().getX(),
                (float) definition.translation().getY(),
                (float) definition.translation().getZ()
        ).add(
                (float) definition.localOffset().getX(),
                (float) definition.localOffset().getY(),
                (float) definition.localOffset().getZ()
        );

        if (definition.rotTranslation()) {
            // if (rotateTranslation) translation = center.add(originalTrans).rotate(rotation);
            return center.add(originalTrans).rotate(rotation);
        } else {
            // else translation = center.rotate(rotation).add(originalTrans);
            // Обратите внимание: center (новый экземпляр) вращается, потом добавляется static offset
            return new Vector3f(center).rotate(rotation).add(originalTrans);
        }
    }
}