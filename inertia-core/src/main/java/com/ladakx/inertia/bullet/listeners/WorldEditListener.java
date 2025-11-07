package com.ladakx.inertia.bullet.listeners;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.block.BlockPos;
import com.ladakx.inertia.nms.bullet.BulletNMSTools;
import com.ladakx.inertia.bullet.space.SpaceManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.ExecutorService;

/**
 * Listener for WorldEdit events.
 * This listener is used to update the Bullet physics engine when blocks are modified.
 */
public class WorldEditListener implements Listener {

	private final SpaceManager spaceManager;
	private final BulletNMSTools nmsTools;

	private final InertiaPlugin instance;
	private final BukkitScheduler bukkitScheduler;
	private final ExecutorService simulationExecutor;

	private final BlockState AIR;

	public WorldEditListener() {
		this.nmsTools = InertiaPlugin.getBulletNMSTools();
		this.spaceManager = InertiaPlugin.getBulletManager().getSpaceManager();

		this.instance = InertiaPlugin.getInstance();
		this.bukkitScheduler = instance.getServer().getScheduler();
		this.simulationExecutor = InertiaPlugin.getSimulationThreadPool().getExecutorService();

		this.AIR = nmsTools.createBlockState(Material.AIR);
	}

	private void blockUpdate(Block block, BlockState blockState) {
		simulationExecutor.execute(() -> {
			spaceManager.getSpace(block.getWorld()).doBlockUpdate(new BlockPos(block), blockState);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		blockUpdate(event.getBlock(), AIR);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockFade(BlockFadeEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockForm(BlockFormEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockGrow(BlockGrowEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonEx(BlockPistonExtendEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonRet(BlockPistonRetractEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLeavesDecay(LeavesDecayEvent event) {
		blockUpdate(event.getBlock(), event.getBlock().getState());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		bukkitScheduler.runTask(instance, () -> {
			blockUpdate(event.getBlock(), AIR);

			for (Block block : event.blockList()) {
				blockUpdate(block, AIR);
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		bukkitScheduler.runTask(instance, () -> {
			for (Block block : event.blockList()) {
				blockUpdate(block, AIR);
			}
		});
	}
}
