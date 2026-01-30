package com.ladakx.inertia.nms.v1_16_r3.render;

import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

public class RenderFactory implements com.ladakx.inertia.rendering.RenderFactory {

    private final ItemModelResolver itemModelResolver;

    public RenderFactory(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public VisualEntity create(World world, Location origin, RenderEntityDefinition def) {
        return spawnEmulatedEntity(world, origin, def);
    }

    private VisualEntity spawnEmulatedEntity(World world, Location origin, RenderEntityDefinition def) {
        ArmorStand stand = world.spawn(origin, ArmorStand.class, s -> {
            s.setGravity(false);
            s.setBasePlate(def.basePlate());
            s.setSmall(def.small());
            s.setArms(def.arms());
            s.setMarker(def.marker());
            s.setInvisible(def.invisible() || isDisplayEntity(def));
            s.setPersistent(false);

            applyContent(s, def);
        });

        return new ArmorStandEntity(stand);
    }

    private boolean isDisplayEntity(RenderEntityDefinition def) {
        return def.kind() == RenderEntityDefinition.EntityKind.BLOCK_DISPLAY ||
               def.kind() == RenderEntityDefinition.EntityKind.ITEM_DISPLAY;
    }

    private void applyContent(ArmorStand stand, RenderEntityDefinition def) {
        ItemStack item = null;

        switch (def.kind()) {
            case ITEM_DISPLAY:
                if (def.itemModelKey() != null) {
                    item = itemModelResolver.resolve(def.itemModelKey());
                }
                if (item == null) item = new ItemStack(Material.BARRIER);

                equipItem(stand, item, def.displayMode());
                break;

            case BLOCK_DISPLAY:
                Material mat = def.blockType();
                if (mat == null) mat = Material.STONE;

                item = new ItemStack(mat); 
                stand.getEquipment().setHelmet(item);
                break;
                
            case ARMOR_STAND:
                if (def.itemModelKey() != null) {
                    item = itemModelResolver.resolve(def.itemModelKey());
                }
                if (item == null) item = new ItemStack(Material.BARRIER);

                equipItem(stand, item, InertiaDisplayMode.HEAD);
                break;
        }
    }

    private void equipItem(ArmorStand stand, ItemStack item, InertiaDisplayMode mode) {
        if (mode == null) mode = InertiaDisplayMode.HEAD;

        switch (mode) {
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
}