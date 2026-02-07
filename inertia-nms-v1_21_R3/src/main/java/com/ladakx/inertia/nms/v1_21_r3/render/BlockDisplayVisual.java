package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.nms.PacketFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.util.List;

public class BlockDisplayVisual extends AbstractNetworkVisual {

    public BlockDisplayVisual(RenderEntityDefinition definition, PacketFactory packetFactory) {
        super(definition, packetFactory);
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        Material material = definition.blockType();
        if (material == null) material = Material.STONE;
        
        // Convert Bukkit Material to NMS BlockState
        CraftBlockData cbd = (CraftBlockData) material.createBlockData();
        BlockState blockState = cbd.getState();

        data.add(SynchedEntityData.DataValue.create(Display.BlockDisplay.DATA_BLOCK_STATE_ID, blockState));
    }
}