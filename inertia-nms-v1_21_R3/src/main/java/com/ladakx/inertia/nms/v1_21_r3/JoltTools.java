package com.ladakx.inertia.nms.v1_21_r3;

import com.github.stephengold.joltjni.AaBox;
import com.ladakx.inertia.nms.v1_21_r3.utils.JoltWrapUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.Collectors;

public class JoltTools implements com.ladakx.inertia.infrastructure.nms.jolt.JoltTools {

    // VarHandle используется для максимально быстрого доступа к полю без создания лишних объектов рефлексии при каждом вызове.
    // Поле nonEmptyBlockCount в LevelChunkSection имеет модификатор package-private, поэтому нужен lookup.
    private static final VarHandle NON_EMPTY_BLOCK_COUNT;
    // Кэшируем массив направлений NMS, чтобы не вызывать values() каждый раз (хотя JVM это оптимизирует, так надежнее)
    private static final net.minecraft.core.Direction[] NMS_DIRECTIONS = net.minecraft.core.Direction.values();

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LevelChunkSection.class, MethodHandles.lookup());
            NON_EMPTY_BLOCK_COUNT = lookup.findVarHandle(LevelChunkSection.class, "nonEmptyBlockCount", short.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public JoltTools() {
    }

    @Override
    public List<AaBox> boundingBoxes(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();

        net.minecraft.world.level.BlockGetter level;
        BlockPos pos;

        if (blockState.isPlaced()) {
            level = ((CraftWorld) blockState.getWorld()).getHandle();
            pos = ((CraftBlock) blockState.getBlock()).getPosition();
        } else {
            level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
            pos = BlockPos.ZERO;
        }

        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(level, pos);
        List<net.minecraft.world.phys.AABB> boundingBoxes = voxelShape.toAabbs();
        return boundingBoxes.stream()
                .map(JoltWrapUtils::convert)
                .collect(Collectors.toList());
    }

    @Override
    public AaBox boundingBox(BlockState blockState) {
        BlockBehaviour.BlockStateBase stateHandle = ((CraftBlockState) blockState).getHandle();

        net.minecraft.world.level.BlockGetter level;
        BlockPos pos;

        if (blockState.isPlaced()) {
            level = ((CraftWorld) blockState.getWorld()).getHandle();
            pos = ((CraftBlock) blockState.getBlock()).getPosition();
        } else {
            level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
            pos = BlockPos.ZERO;
        }

        net.minecraft.world.phys.shapes.VoxelShape voxelShape = stateHandle.getCollisionShape(level, pos);
        if (voxelShape.isEmpty()) {
            return null;
        } else {
            return JoltWrapUtils.convert(voxelShape.bounds());
        }
    }

    @Override
    public int getBlockId(BlockState blockState) {
        // Быстрое получение ID состояния блока для сериализации/сравнения
        return net.minecraft.world.level.block.Block.getId(((CraftBlockState) blockState).getHandle());
    }

    @Override
    public boolean equalsById(BlockState blockState1, BlockState blockState2) {
        return getBlockId(blockState1) == getBlockId(blockState2);
    }

    @Override
    public BlockState createBlockState(Material material) {
        return Bukkit.createBlockData(material).createBlockState();
    }

    @Override
    public BlockState getBlockState(Chunk chunk, int x, int y, int z) {
        // Метод общего назначения, delegate к Bukkit API, который внутри использует NMS
        return chunk.getBlock(x, y, z).getState();
    }

    @Override
    public AaBox getOcclusionShape(Chunk chunk, int x, int y, int z) {
        // В текущей реализации для физики occlusion shape часто избыточен по сравнению с collision shape
        return null;
    }

    @Override
    public boolean renderFace(World world, Block block, com.ladakx.inertia.common.Direction face) {
        // Оптимизированная проверка видимости грани (culling) через NMS
        ServerLevel level = ((CraftWorld) world).getHandle();
        CraftBlockState craftBlockState = (CraftBlockState) block.getState();
        net.minecraft.world.level.block.state.BlockState base = craftBlockState.getHandle();
        BlockPos pos = craftBlockState.getPosition();

        // Быстрое смещение позиции
        BlockPos otherPos = pos.relative(NMS_DIRECTIONS[face.ordinal()]);
        net.minecraft.world.level.block.state.BlockState other = level.getBlockState(otherPos);

        return net.minecraft.world.level.block.Block.shouldRenderFace(base, other, NMS_DIRECTIONS[face.ordinal()]);
    }

    @Override
    public int getSectionsCount(Chunk chunk) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);
        return ch.getSectionsCount();
    }

    @Override
    public int getMinSectionY(Chunk chunk) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        ChunkAccess ch = craftChunk.getHandle(ChunkStatus.FULL);

        return ch.getMinSectionY();
    }

    @Override
    public int getMaxSectionY(Chunk chunk) {
        return ((CraftChunk) chunk).getHandle(ChunkStatus.FULL).getMaxSectionY();
    }

    @Override
    public boolean hasOnlyAir(Chunk chunk, int numSect) {
        ChunkAccess nmsChunk = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        LevelChunkSection[] sections = nmsChunk.getSections();
        int idx = numSect - nmsChunk.getMinSectionY();

        // Безопасная проверка индекса
        if (idx < 0 || idx >= sections.length) return true;

        LevelChunkSection section = sections[idx];
        // Если секция null или пустая (hasOnlyAir полагается на счетчик блоков non-empty)
        return section == null || section.hasOnlyAir();
    }

    @Override
    public boolean isSectionFull(Chunk chunk, int numSect) {
        return getSectionsNonEmptyBlocks(chunk, numSect) == 4096;
    }

    @Override
    public short getSectionsNonEmptyBlocks(Chunk chunk, int numSect) {
        ChunkAccess nmsChunk = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        LevelChunkSection[] sections = nmsChunk.getSections();
        int idx = numSect - nmsChunk.getMinSectionY();

        if (idx < 0 || idx >= sections.length) return 0;

        LevelChunkSection section = sections[idx];
        if (section == null) return 0;

        // Используем VarHandle для чтения package-private поля nonEmptyBlockCount
        // Это быстрее, чем Reflection API при частом вызове
        return (short) NON_EMPTY_BLOCK_COUNT.get(section);
    }

    /**
     * Высокопроизводительный метод для получения Material в циклах (например, Greedy Meshing).
     * Избегает создания объектов CraftBlock и CraftBlockState.
     */
    @Override
    public Material getMaterial(Chunk chunk, int numSect, int x, int y, int z) {
        ChunkAccess nmsChunk = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        LevelChunkSection[] sections = nmsChunk.getSections();
        int idx = numSect - nmsChunk.getMinSectionY();

        if (idx < 0 || idx >= sections.length) return Material.AIR;

        LevelChunkSection section = sections[idx];
        if (section == null || section.hasOnlyAir()) return Material.AIR;

        // Прямой доступ к палитре состояний без проверок координат мира (они локальны для секции 0-15)
        // x, y, z ожидаются в пределах 0-15
        net.minecraft.world.level.block.state.BlockState nmsState = section.getBlockState(x, y, z);

        // Быстрая конвертация NMS Block -> Bukkit Material
        return CraftMagicNumbers.getMaterial(nmsState.getBlock());
    }

    /**
     * Создает CraftBlockState. Относительно тяжелая операция, используйте getMaterial, если нужен только тип.
     */
    @Override
    public BlockState getBlockState(Chunk chunk, int numSect, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
        var section = chunkAccess.getSection(numSect);

        return (BlockState) section.getStates().get(x,y,z);
    }

    @Override
    public int getBlockSkyLight(Chunk chunk, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkSnap = chunk.getChunkSnapshot();

        return chunkSnap.getBlockSkyLight(x-(craftChunk.getX()<<4), y, z-(craftChunk.getZ()<<4));
    }

    @Override
    public int getBlockEmittedLight(Chunk chunk, int x, int y, int z) {
        var craftChunk = (CraftChunk) chunk;
        var chunkSnap = chunk.getChunkSnapshot();

        return chunkSnap.getBlockEmittedLight(x-(craftChunk.getX()<<4), y, z-(craftChunk.getZ()<<4));
    }
}