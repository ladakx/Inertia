package com.ladakx.inertia.physics.listeners;

import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class BlockChangeListener implements Listener {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public BlockChangeListener(PhysicsWorldRegistry physicsWorldRegistry) {
        this.physicsWorldRegistry = physicsWorldRegistry;
    }

    // --- Основные события строительства ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        // Иногда полезно для предсказания разрушения, но обычно достаточно Break
        // handleUpdate(event.getBlock());
    }

    // --- Взрывы ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleWorldUpdate(event.getBlock().getWorld());
        for (Block block : event.blockList()) {
            handleUpdate(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleWorldUpdate(event.getLocation().getWorld());
        for (Block block : event.blockList()) {
            handleUpdate(block);
        }
    }

    // --- Поршни ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handleUpdate(event.getBlock()); // Сам поршень (голова)
        for (Block block : event.getBlocks()) {
            handleUpdate(block); // Старая позиция
            handleUpdate(block.getRelative(event.getDirection())); // Новая позиция
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handleUpdate(event.getBlock());
        for (Block block : event.getBlocks()) {
            handleUpdate(block);
            handleUpdate(block.getRelative(event.getDirection()));
        }
    }

    // --- Природные изменения ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        // Таяние льда, снега, высыхание коралла
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        // Образование снега, льда, булыжника из лавы+воды, затвердевание бетона
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        // Выращивание деревьев из саженцев
        for (BlockState state : event.getBlocks()) {
            handleUpdate(state.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        // Распространение огня, грибов, травы
        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Эндермены, падающие блоки, овцы едят траву
//        handleUpdate(event.getBlock());
    }

    // --- Жидкости и Ведра ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
//        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
//        handleUpdate(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // Течение жидкости.
        // ВАЖНО: Это событие спамит. Если у вас нет физики жидкостей, лучше закомментировать.
        // Для GreedyMesh это вызовет много перестроений.
        // handleUpdate(event.getToBlock());
    }

    // --- Взаимодействие (Запрошено пользователем) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockHandleCreative(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) return;

        // Обработка ЛКМ (ломка в креативе, удар)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            handleUpdate(event.getClickedBlock());
        }
        // Обработка ПКМ (изменение состояния: открытие дверей, нажатие кнопок, рычагов)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            // Мы обновляем блок, так как его коллизия могла измениться (дверь открылась)
            handleUpdate(event.getClickedBlock());
        }
    }

    // --- Вспомогательные методы ---

    private void handleUpdate(Block block) {
        PhysicsWorld space = physicsWorldRegistry.getSpace(block.getWorld());
        if (space != null) {
            space.onBlockChange(block.getX(), block.getY(), block.getZ());
        }
    }

    private void handleWorldUpdate(org.bukkit.World world) {
        // Метод-заглушка, если нужно обновить глобальное состояние мира
    }
}