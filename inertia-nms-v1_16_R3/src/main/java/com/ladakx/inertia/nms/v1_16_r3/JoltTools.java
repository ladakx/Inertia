package com.ladakx.inertia.nms.v1_16_r3;

import com.github.stephengold.joltjni.AaBox;
import com.ladakx.inertia.nms.v1_16_r3.utils.JoltWrapUtils;
import com.ladakx.inertia.common.Direction;
import net.minecraft.server.v1_16_R3.AxisAlignedBB;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlockState;

import java.util.List;
import java.util.stream.Collectors;

public class JoltTools implements com.ladakx.inertia.infrastructure.nms.jolt.JoltTools {

    public JoltTools() {
        // Ensure Jolt is initialized
    }

    @Override
    public List<AaBox> boundingBoxes(BlockState blockState) {
        var stateHandle = ((CraftBlockState) blockState).getHandle();
        var blockPosHandle = ((CraftBlock) blockState.getBlock()).getPosition();
        var worldHandle = ((CraftWorld) blockState.getWorld()).getHandle();
        var voxelShape = stateHandle.getCollisionShape(worldHandle, blockPosHandle);

        List<AxisAlignedBB> boundingBoxes = voxelShape.d();

        return boundingBoxes.stream()
                .map(JoltWrapUtils::convert)
                .collect(Collectors.toList());
    }

    @Override
    public int getBlockId(BlockState blockState) {
        return net.minecraft.server.v1_16_R3.Block.getCombinedId(((CraftBlockState) blockState).getHandle());
    }

    @Override
    public boolean equalsById(BlockState blockState1, BlockState blockState2) {
        return getBlockId(blockState1) == getBlockId(blockState2);
    }

    @Override
    public AaBox boundingBox(BlockState blockState) {
        var stateHandle = ((CraftBlockState) blockState).getHandle();
        var voxelShape = stateHandle.getCollisionShape(((CraftBlock) blockState.getBlock()).getCraftWorld().getHandle(), ((CraftBlock) blockState.getBlock()).getPosition());
        if (voxelShape.isEmpty()) {
            return null;
        } else {
            return JoltWrapUtils.convert(voxelShape.getBoundingBox());
        }
    }

    @Override
    public BlockState createBlockState(Material material) {
        return new CraftBlockState(material);
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int x, int y, int z) {
        return null;
    }

    @Override
    public AaBox getOcclusionShape(Chunk chunk, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean renderFace(World world, Block block, Direction face) {
        return false;
    }

    @Override
    public int getSectionsCount(Chunk chunk) {
        return 0;
    }

    @Override
    public int getMinSectionY(Chunk chunk) {
        return 0;
    }

    @Override
    public int getMaxSectionY(Chunk chunk) {
        return 0;
    }

    @Override
    public boolean hasOnlyAir(Chunk chunk, int numSect) {
        return false;
    }

    @Override
    public short getSectionsNonEmptyBlocks(Chunk chunk, int numSect) {
        return 0;
    }

    @Override
    public boolean isSectionFull(Chunk chunk, int numSect) {
        return false;
    }

    @Override
    public Material getMaterial(Chunk chunk, int numSect, int x, int y, int z) {
        return null;
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int numSect, int x, int y, int z) {
        return null;
    }

    @Override
    public int getBlockSkyLight(Chunk chunk, int x, int y, int z) {
        return 0;
    }

    @Override
    public int getBlockEmittedLight(Chunk chunk, int x, int y, int z) {
        return 0;
    }
}