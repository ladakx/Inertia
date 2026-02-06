package com.ladakx.inertia.nms.v1_16_r3.render;

import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import net.minecraft.server.v1_16_R3.EntityArmorStand;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftArmorStand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.EulerAngle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.ladakx.inertia.common.utils.RotationUtils.toEulerAngle;

public class ArmorStandEntity implements VisualEntity {
    private final ArmorStand stand;
    private final RenderEntityDefinition.EntityKind kind;
    private final InertiaDisplayMode displayMode;

    public ArmorStandEntity(ArmorStand stand, RenderEntityDefinition.EntityKind kind, InertiaDisplayMode displayMode) {
        this.stand = stand;
        this.kind = kind;
        this.displayMode = displayMode;
    }

    @Override
    public void update(Location location, Quaternionf rotation, Vector3f center, boolean rotLocalOff) {
        if (!stand.isValid()) return;

        // Optimization: Use NMS direct setLocation if within the same chunk to avoid heavy Bukkit teleport overhead
        int oldCX = stand.getLocation().getBlockX() >> 4;
        int oldCZ = stand.getLocation().getBlockZ() >> 4;
        int newCX = location.getBlockX() >> 4;
        int newCZ = location.getBlockZ() >> 4;

        if (oldCX == newCX && oldCZ == newCZ) {
            EntityArmorStand handle = ((CraftArmorStand) stand).getHandle();
            // setLocation updates position, bounding box and chunk position references lightly
            handle.setLocation(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
            // The server's entity tracker will detect the position change in the next tick and send packets automatically.
        } else {
            stand.teleport(location);
        }

        EulerAngle angle = toEulerAngle(rotation);
        stand.setHeadPose(angle);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!stand.isValid()) return;
        stand.setInvisible(!visible);
    }

    @Override
    public void remove() {
        if (stand.isValid()) stand.remove();
    }

    @Override
    public boolean isValid() {
        return stand.isValid();
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        return stand.getPersistentDataContainer();
    }

    @Override
    public void setGlowing(boolean glowing) {
        stand.setGlowing(glowing);
    }

    @Override
    public boolean setItemStack(ItemStack stack) {
        if (!stand.isValid()) return false;
        if (stand.getEquipment() == null) return false;

        if (kind == RenderEntityDefinition.EntityKind.ITEM_DISPLAY) {
            equipItem(stack, displayMode);
            return true;
        }
        if (kind == RenderEntityDefinition.EntityKind.ARMOR_STAND) {
            stand.getEquipment().setHelmet(stack);
            return true;
        }
        return false;
    }

    private void equipItem(ItemStack item, InertiaDisplayMode mode) {
        InertiaDisplayMode finalMode = (mode == null) ? InertiaDisplayMode.HEAD : mode;
        switch (finalMode) {
            case THIRDPERSON_RIGHTHAND:
            case FIRSTPERSON_RIGHTHAND:
                stand.getEquipment().setItemInMainHand(item);
                break;
            case THIRDPERSON_LEFTHAND:
            case FIRSTPERSON_LEFTHAND:
                stand.getEquipment().setItemInOffHand(item);
                break;
            default:
                stand.getEquipment().setHelmet(item);
                break;
        }
    }

    @Override
    public boolean getPersistent() {
        return stand.isPersistent();
    }

    @Override
    public void setPersistent(boolean persistent) {
        stand.setPersistent(persistent);
    }
}
