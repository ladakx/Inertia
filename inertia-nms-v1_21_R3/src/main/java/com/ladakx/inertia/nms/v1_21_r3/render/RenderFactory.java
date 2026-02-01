package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.VisualEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RenderFactory implements com.ladakx.inertia.rendering.RenderFactory {

    private final ItemModelResolver itemModelResolver;

    public RenderFactory(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public VisualEntity create(World world, Location origin, RenderEntityDefinition def) {
        ensureChunkLoaded(world, origin);

        return switch (def.kind()) {
            case BLOCK_DISPLAY -> spawnBlockDisplay(world, origin, def);
            case ITEM_DISPLAY -> spawnItemDisplay(world, origin, def);
            case ARMOR_STAND -> spawnArmorStand(world, origin, def);
        };
    }

    private void ensureChunkLoaded(World world, Location loc) {
        int x = loc.getBlockX() >> 4;
        int z = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(x, z)) {
            // В 1.21 лучше использовать addPluginChunkTicket, но для совместимости API используем простой load
            // Spigot сам подгрузит чанк для entity spawn, но явный вызов надежнее
            world.getChunkAt(x, z).load(true);
        }
    }

    private VisualEntity spawnBlockDisplay(World world, Location origin, RenderEntityDefinition def) {
        BlockDisplay display = world.spawn(origin, BlockDisplay.class, entity -> {
            applyCommonDisplayTraits(entity, def);
            if (def.blockType() != null) {
                entity.setBlock(def.blockType().createBlockData());
            } else {
                entity.setBlock(Material.STONE.createBlockData());
            }
        });
        return new DisplayEntity(display);
    }

    private VisualEntity spawnItemDisplay(World world, Location origin, RenderEntityDefinition def) {
        ItemDisplay display = world.spawn(origin, ItemDisplay.class, entity -> {
            applyCommonDisplayTraits(entity, def);

            ItemStack stack = itemModelResolver.resolve(def.itemModelKey());
            entity.setItemStack(stack);

            if (def.displayMode() != null) {
                try {
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(def.displayMode().name()));
                } catch (IllegalArgumentException ignored) {
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                }
            }
        });
        return new DisplayEntity(display);
    }

    private VisualEntity spawnArmorStand(World world, Location origin, RenderEntityDefinition def) {
        ArmorStand stand = world.spawn(origin, ArmorStand.class, entity -> {
            entity.setGravity(false);
            entity.setBasePlate(def.basePlate());
            entity.setSmall(def.small());
            entity.setArms(def.arms());
            entity.setMarker(def.marker());
            entity.setInvisible(def.invisible());
            entity.setPersistent(false);
        });

        return new ArmorStandEntity(stand);
    }

    private void applyCommonDisplayTraits(Display display, RenderEntityDefinition def) {
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

        display.setDisplayWidth(2f);
        display.setDisplayHeight(2f);

        display.setTransformation(new Transformation(
                new Vector3f((float) def.translation().getX(), (float) def.translation().getY(), (float) def.translation().getZ()),
                new Quaternionf(def.localRotation().x(), def.localRotation().y(), def.localRotation().z(), def.localRotation().w()),
                new Vector3f((float)def.scale().getX(), (float)def.scale().getY(), (float)def.scale().getZ()),
                new Quaternionf()
        ));

        display.setPersistent(false);
    }
}