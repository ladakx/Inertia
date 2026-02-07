package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.api.service.PhysicsManipulationService;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.NetworkInteractTool;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.data.ToolDataManager;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.List;

public class DeleteTool extends Tool implements NetworkInteractTool {

    private final PhysicsWorldRegistry physicsWorldRegistry;
    private final PhysicsManipulationService manipulationService;

    public DeleteTool(ConfigurationService configurationService,
                      PhysicsWorldRegistry physicsWorldRegistry,
                      PhysicsManipulationService manipulationService,
                      ToolDataManager toolDataManager) {
        super("remover", configurationService, toolDataManager);
        this.physicsWorldRegistry = physicsWorldRegistry;
        this.manipulationService = manipulationService;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());

        if (space != null && tryRemoveActiveBody(player, space)) {
            return;
        }
        tryRemoveStaticEntity(player);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
    }

    @Override
    public void onSwapHands(Player player) {
    }

    @Override
    public void onNetworkInteract(Player player, AbstractPhysicsBody body, boolean attack) {
        if (!validateWorld(player)) return;
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());
        if (space == null) return;
        int removed = manipulationService.removeCluster(space, body);
        if (removed > 0) {
            playSuccessEffect(player);
        }
    }

    private boolean tryRemoveActiveBody(Player player, PhysicsWorld space) {
        var eye = player.getEyeLocation();
        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(eye, eye.getDirection(), 16);
        if (results.isEmpty()) return false;

        AbstractPhysicsBody hitBody = space.getObjectByVa(results.get(0).va());
        if (hitBody == null) return false;

        int removed = manipulationService.removeCluster(space, hitBody);

        if (removed > 0) {
            playSuccessEffect(player);
            return true;
        }
        return false;
    }

    private void tryRemoveStaticEntity(Player player) {
        var eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye,
                eye.getDirection(),
                16.0,
                0.5,
                entity -> entity.getPersistentDataContainer().has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING)
        );

        if (result == null || result.getHitEntity() == null) return;

        int removed = manipulationService.removeStaticCluster(result.getHitEntity());
        if (removed > 0) {
            playSuccessEffect(player);
        }
    }

    private void playSuccessEffect(Player player) {
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_STONE_BREAK,
                SoundCategory.MASTER,
                0.5F,
                0.6F
        );
        send(player, MessageKey.REMOVER_USED);
    }

    @Override
    protected ItemStack getBaseItem() {
        ItemStack item = new ItemStack(Material.TNT_MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            MessageManager msg = configurationService.getMessageManager();
            meta.displayName(msg.getSingle(MessageKey.TOOL_REMOVER_NAME));
            meta.lore(msg.get(MessageKey.TOOL_REMOVER_LORE));
            item.setItemMeta(meta);
        }
        return item;
    }
}
