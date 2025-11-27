package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.render.ItemModelResolver;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.nms.render.runtime.VisualObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public class RenderFactory implements com.ladakx.inertia.nms.render.RenderFactory {

    private final ItemModelResolver itemModelResolver;

    public RenderFactory(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public VisualObject create(World world, Location origin, RenderEntityDefinition def) {
        return switch (def.kind()) {
            case BLOCK_DISPLAY -> spawnBlockDisplay(world, origin, def);
            case ITEM_DISPLAY -> spawnItemDisplay(world, origin, def);
            case ARMOR_STAND -> spawnArmorStand(world, origin, def);
        };
    }

    private VisualObject spawnBlockDisplay(World world, Location origin, RenderEntityDefinition def) {
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, entity -> {
            applyCommonDisplayTraits(entity, def);
            if (def.blockType() != null) {
                entity.setBlock(def.blockType().createBlockData());
            } else {
                entity.setBlock(Material.STONE.createBlockData());
            }
        });
        return new DisplayObject(display);
    }

    private VisualObject spawnItemDisplay(World world, Location origin, RenderEntityDefinition def) {
        ItemDisplay display = world.spawn(origin, ItemDisplay.class, entity -> {
            applyCommonDisplayTraits(entity, def);
            
            ItemStack stack = null;
            if (def.itemModelKey() != null && itemModelResolver.canResolve(def.itemModelKey())) {
                stack = itemModelResolver.resolve(def.itemModelKey());
            }

            if (def.itemModelKey() != null && Material.getMaterial(def.itemModelKey()) != null) {
                stack = new ItemStack(Objects.requireNonNull(Material.getMaterial(def.itemModelKey())));
            }

            if (stack == null) {
                stack = new ItemStack(Material.BARRIER);
            }

            entity.setItemStack(stack);

            if (def.displayMode() != null) {
                try {
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(def.displayMode().name()));
                } catch (IllegalArgumentException ignored) {
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                }
            }
        });
        return new DisplayObject(display);
    }

    private VisualObject spawnArmorStand(World world, Location origin, RenderEntityDefinition def) {
        ArmorStand stand = world.spawn(origin, ArmorStand.class, entity -> {
            entity.setGravity(false);
            entity.setBasePlate(def.basePlate());
            entity.setSmall(def.small());
            entity.setArms(def.arms());
            entity.setMarker(def.marker());
            entity.setInvisible(def.invisible());
            entity.setPersistent(false);
        });

        return new ArmorStandObject(stand);
    }

    private void applyCommonDisplayTraits(Display display, RenderEntityDefinition def) {
        display.setPersistent(false);
        if (def.viewRange() != null) display.setViewRange(def.viewRange());
        if (def.shadowRadius() != null) display.setShadowRadius(def.shadowRadius());
        if (def.shadowStrength() != null) display.setShadowStrength(def.shadowStrength());
        
        if (def.billboard() != null) {
            display.setBillboard(Display.Billboard.valueOf(def.billboard().name()));
        }
        
        if (def.interpolationDuration() != null) {
            display.setInterpolationDuration(def.interpolationDuration());
        }
        
        if (def.teleportDuration() != null) {
            display.setTeleportDuration(def.teleportDuration());
        }

        if (def.brightnessBlock() != null || def.brightnessSky() != null) {
            int b = def.brightnessBlock() == null ? 0 : def.brightnessBlock();
            int s = def.brightnessSky() == null ? 0 : def.brightnessSky();
            display.setBrightness(new Display.Brightness(b, s));
        }

        display.setTransformation(new Transformation(
                new Vector3f(0,0,0),
                new Quaternionf(),
                new Vector3f((float)def.scale().getX(), (float)def.scale().getY(), (float)def.scale().getZ()),
                new Quaternionf()
        ));
    }
}