package com.ladakx.inertia.features.tools.impl;

import com.ladakx.inertia.common.PhysicsGraphUtils;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.common.pdc.InertiaPDCKeys;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.configuration.message.MessageManager;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import com.ladakx.inertia.physics.world.PhysicsWorld;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.*;

/**
 * Инструмент для удаления физических объектов и статичных декораций.
 * Поддерживает удаление целых кластеров (цепи, рэгдоллы).
 */
public class DeleteTool extends Tool {

    private final PhysicsWorldRegistry physicsWorldRegistry;

    public DeleteTool(ConfigurationService configurationService, PhysicsWorldRegistry physicsWorldRegistry) {
        super("remover", configurationService);
        this.physicsWorldRegistry = physicsWorldRegistry;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PhysicsWorld space = physicsWorldRegistry.getSpace(player.getWorld());

        // 1. Попытка удалить АКТИВНОЕ физическое тело (Jolt Raycast)
        if (space != null && tryRemoveActiveBody(player, space)) {
            return;
        }

        // 2. Попытка удалить СТАТИЧНУЮ декорацию (Bukkit RayTrace)
        tryRemoveStaticEntity(player);
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        // Placeholder
    }

    @Override
    public void onSwapHands(Player player) {
    }

    /**
     * Логика удаления активных тел.
     * Использует PhysicsGraphUtils для поиска связанных частей.
     */
    private boolean tryRemoveActiveBody(Player player, PhysicsWorld space) {
        List<PhysicsWorld.RaycastResult> results = space.raycastEntity(player.getEyeLocation(), player.getLocation().getDirection(), 16);
        if (results.isEmpty()) return false;

        PhysicsWorld.RaycastResult result = results.get(0);
        Long va = result.va();
        if (va == null) return false;

        AbstractPhysicsBody hitBody = space.getObjectByVa(va);
        if (hitBody == null) return false;

        // REUSE: Используем общий утилит для поиска всех связанных частей
        Set<AbstractPhysicsBody> cluster = PhysicsGraphUtils.collectConnectedBodies(space, hitBody);

        for (AbstractPhysicsBody body : cluster) {
            try {
                body.destroy();
            } catch (Exception e) {
                InertiaLogger.error("Failed to destroy physics object via RemoverTool: " + e);
            }
        }

        playSuccessEffect(player);
        return true;
    }

    /**
     * Логика удаления статичных (замороженных) сущностей.
     * Использует ClusterID и алгоритм заливки (Flood Fill) для поиска группы.
     */
    private void tryRemoveStaticEntity(Player player) {
        // Bukkit RayTrace ищет только сущности с нашим флагом static
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                16.0,
                0.5,
                entity -> entity.getPersistentDataContainer().has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING)
        );

        if (result == null || result.getHitEntity() == null) return;

        Entity hitEntity = result.getHitEntity();
        var pdc = hitEntity.getPersistentDataContainer();

        // Проверяем, принадлежит ли сущность к группе (кластеру)
        String clusterIdStr = pdc.get(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING);

        if (clusterIdStr != null) {
            removeClusterRecursive(hitEntity, clusterIdStr);
        } else {
            // Это одиночный объект (блок/ТНТ) или старый объект без ID — удаляем только его
            hitEntity.remove();
        }

        playSuccessEffect(player);
    }

    /**
     * Рекурсивно (через очередь) удаляет все сущности с заданным ClusterID,
     * находящиеся поблизости друг от друга.
     * Эффективно проходит по всей длине цепи любой протяженности.
     */
    private void removeClusterRecursive(Entity startEntity, String targetClusterId) {
        Set<UUID> processedEntities = new HashSet<>();
        Deque<Entity> queue = new ArrayDeque<>();

        // Добавляем стартовую сущность
        queue.add(startEntity);
        processedEntities.add(startEntity.getUniqueId());

        while (!queue.isEmpty()) {
            Entity current = queue.poll();

            // Удаляем текущую сущность
            if (current.isValid()) {
                current.remove();
            }

            // Ищем соседей в радиусе 3 блоков (достаточно для захвата следующего звена)
            // getNearbyEntities эффективен, так как работает с чанками
            List<Entity> neighbors = current.getNearbyEntities(2.0, 2.0, 2.0);

            for (Entity neighbor : neighbors) {
                // Пропускаем уже обработанные (защита от зацикливания)
                if (processedEntities.contains(neighbor.getUniqueId())) continue;

                var nPdc = neighbor.getPersistentDataContainer();

                // Пропускаем, если это не статика Inertia
                if (!nPdc.has(InertiaPDCKeys.INERTIA_ENTITY_STATIC, PersistentDataType.STRING)) continue;

                // Проверяем совпадение ClusterID
                String nClusterId = nPdc.get(InertiaPDCKeys.INERTIA_CLUSTER_UUID, PersistentDataType.STRING);

                if (targetClusterId.equals(nClusterId)) {
                    processedEntities.add(neighbor.getUniqueId());
                    queue.add(neighbor);
                }
            }
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