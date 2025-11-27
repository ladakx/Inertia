package com.ladakx.inertia.tools.impl;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.api.InertiaAPI;
import com.ladakx.inertia.files.config.ConfigManager;
import com.ladakx.inertia.jolt.object.ChainPhysicsObject;
import com.ladakx.inertia.jolt.object.PhysicsObjectType;
import com.ladakx.inertia.jolt.space.MinecraftSpace;
import com.ladakx.inertia.jolt.space.SpaceManager;
import com.ladakx.inertia.physics.config.ChainBodyDefinition;
import com.ladakx.inertia.physics.registry.PhysicsModelRegistry;
import com.ladakx.inertia.tools.Tool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChainTool extends Tool {

    public static final NamespacedKey BODY_ID_KEY = new NamespacedKey(InertiaPlugin.getInstance(), "chain_body_id");

    // Зберігання першої точки для кожного гравця
    private final Map<UUID, Location> startPoints = new HashMap<>();

    public ChainTool() {
        super("chain_tool");
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        // 1. Визначення локації (клік по блоку або повітрю)
        Location loc = getTargetLocation(player, event);
        if (loc == null) return;

        // 2. Збереження першої точки
        startPoints.put(player.getUniqueId(), loc);

        player.sendMessage(Component.text("Position 1 set at: " + formatLoc(loc), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.5f);
        event.setCancelled(true);
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!validateWorld(player)) return;

        // 1. Перевірка наявності першої точки
        if (!startPoints.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Please select Position 1 first (Left Click)!", NamedTextColor.RED));
            return;
        }

        // 2. Отримання ID ланцюга з інструмента
        String bodyId = getBodyIdFromItem(event.getItem());
        if (bodyId == null) {
            player.sendMessage(Component.text("Invalid Tool: Missing Body ID.", NamedTextColor.RED));
            return;
        }

        // 3. Визначення другої точки
        Location endLoc = getTargetLocation(player, event);
        Location startLoc = startPoints.remove(player.getUniqueId()); // Видаляємо після використання

        player.sendMessage(Component.text("Position 2 set. Building chain...", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        event.setCancelled(true);

        // 4. Побудова ланцюга між двома точками
        buildChainBetweenPoints(player, startLoc, endLoc, bodyId);
    }

    @Override
    public void onSwapHands(Player player) {
        // Скидання виділення
        if (startPoints.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Selection cleared.", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 0.5f);
        }
    }

    /**
     * Основна логіка розрахунку та спавну ланцюга між двома точками.
     */
    public static void buildChainBetweenPoints(Player player, Location start, Location end, String bodyId) {
        PhysicsModelRegistry registry = ConfigManager.getInstance().getPhysicsModelRegistry();
        Optional<PhysicsModelRegistry.BodyModel> modelOpt = registry.find(bodyId);

        if (modelOpt.isEmpty() || modelOpt.get().bodyDefinition().type() != PhysicsObjectType.CHAIN) {
            player.sendMessage(Component.text("Invalid chain body definition: " + bodyId, NamedTextColor.RED));
            return;
        }

        ChainBodyDefinition def = (ChainBodyDefinition) modelOpt.get().bodyDefinition();
        MinecraftSpace space = SpaceManager.getInstance().getSpace(player.getWorld());
        if (space == null) return;

        // --- Математика Векторів ---

        // Вектор від А до Б
        Vector directionVector = end.toVector().subtract(start.toVector());
        double totalDistance = directionVector.length();

        // Нормалізований напрямок
        Vector direction = directionVector.clone().normalize();

        // Параметри з конфігу
        double spacing = def.chainSettings().spacing();

        // Розрахунок кількості ланок
        // Ми беремо ceil, щоб ланцюг точно дістав до кінця (може трохи провисати)
        int linkCount = (int) Math.ceil(totalDistance / spacing);

        // Запобіжник від крашу (якщо точки співпали)
        if (linkCount < 1) linkCount = 1;

        // Розрахунок ротації (Quaternion)
        // Нам потрібно повернути об'єкт так, щоб його вісь Y (зазвичай довга сторона ланцюга)
        // дивилася вздовж вектора direction.
        // Paper API (JOML) допомагає нам створити кватерніон "Look Rotation".
        // В Minecraft "вперед" це -Z, "вверх" це +Y.
        // Для ланцюгів зазвичай вісь Y - це вісь з'єднання.

        // Створюємо кватерніон, який обертає вектор UP (0,1,0) до нашого direction
        Quaternionf jomlQuat = new Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0),
                new org.joml.Vector3f((float)direction.getX(), (float)direction.getY(), (float)direction.getZ()));

        Quat linkRotation = new Quat(jomlQuat.x, jomlQuat.y, jomlQuat.z, jomlQuat.w);

        Body parentBody = null;

        for (int i = 0; i <= linkCount; i++) {
            // Лінійна інтерполяція позиції
            // Якщо i=0 -> start, якщо i=linkCount -> end (або близько до end)
            double distanceTraveled = i * spacing;

            // Якщо ми вийшли за межі загальної довжини, зупиняємось (або корегуємо останню ланку)
            if (distanceTraveled > totalDistance) {
                // Опціонально: можна не спавнити останню, якщо вона сильно вилазить
                // Але для "закріплення" краще заспавнити її точно в точці B
            }

            Vector offset = direction.clone().multiply(distanceTraveled);
            Location currentLoc = start.clone().add(offset);

            // Для останньої ланки форсуємо точну позицію End
            if (i == linkCount) {
                currentLoc = end.clone();
            }

            RVec3 pos = new RVec3(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

            try {
                ChainPhysicsObject link = new ChainPhysicsObject(
                        space,
                        bodyId,
                        registry,
                        InertiaPlugin.getInstance().getRenderFactory(),
                        pos,
                        linkRotation,
                        parentBody
                );

                // --- Закріплення (Anchoring) ---
                // Перша ланка (Start) - Static
                // Остання ланка (End) - Static
                if (i == 0 || i == linkCount) {
                    space.getBodyInterface().setMotionType(
                            link.getBody().getId(),
                            EMotionType.Static,
                            EActivation.DontActivate
                    );
                }

                parentBody = link.getBody();

            } catch (Exception e) {
                InertiaLogger.error("Failed to spawn chain link " + i, e);
                break;
            }
        }

        player.sendMessage(Component.text("Chain created with " + (linkCount + 1) + " links.", NamedTextColor.GRAY));
    }

    // --- Helper Methods ---

    private boolean validateWorld(Player player) {
        if (!InertiaAPI.get().isWorldSimulated(player.getWorld().getName())) {
            player.sendMessage(Component.text("Physics not enabled in this world.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private Location getTargetLocation(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            // Клік по блоку - беремо центр грані, на яку клікнули (або просто центр блоку)
            // Для простоти беремо локацію дотику + невеликий зсув до гравця
            return event.getInteractionPoint() != null
                    ? event.getInteractionPoint()
                    : event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        } else {
            // Клік в повітря - RayTrace на 5 блоків
            return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(3));
        }
    }

    private String formatLoc(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    public ItemStack getToolItem(String bodyId) {
        ItemStack item = getBaseItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(BODY_ID_KEY, PersistentDataType.STRING, bodyId);
            meta.displayName(Component.text("Chain Tool: ", NamedTextColor.GOLD)
                    .append(Component.text(bodyId, NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("L-Click: Set Point 1", NamedTextColor.GRAY),
                    Component.text("R-Click: Set Point 2 & Build", NamedTextColor.GRAY),
                    Component.text("Swap (F): Clear selection", NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
        }
        return super.markItemAsTool(item);
    }

    private String getBodyIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(BODY_ID_KEY, PersistentDataType.STRING);
    }

    @Override
    protected ItemStack getBaseItem() {
        return new ItemStack(Material.LEAD); // Поводок виглядає логічніше для "з'єднання"
    }
}