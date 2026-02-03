package com.ladakx.inertia.nms.v1_21_r2.render;

import com.ladakx.inertia.nms.v1_21_r2.render.ArmorStandEntity;
import com.ladakx.inertia.nms.v1_21_r2.render.DisplayEntity;
import com.ladakx.inertia.nms.v1_21_r2.render.EmptyVisual;
import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.VisualEntity;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
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

    // Реализация создания линии через BlockDisplay
    @Override
    public VisualEntity createDebugLine(World world, Vector start, Vector end, float thickness, Color color) {
        if (start.equals(end)) return new EmptyVisual();

        Location midpoint = start.clone().add(end).multiply(0.5).toLocation(world);
        Vector direction = end.clone().subtract(start);
        float length = (float) direction.length();

        if (length < 0.001f) return new EmptyVisual();
        ensureChunkLoaded(world, midpoint);

        BlockDisplay display = (BlockDisplay) world.spawnEntity(midpoint, EntityType.BLOCK_DISPLAY);

        Material mat = getColorMaterial(color);
        display.setBlock(mat.createBlockData());
        display.setGlowColorOverride(color);
        display.setGlowing(true);
        display.setPersistent(false);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setVisibleByDefault(false); // Скрываем по умолчанию

        // Математика трансформации
        Vector3f dir = new Vector3f((float)direction.getX(), (float)direction.getY(), (float)direction.getZ()).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
        Vector3f scale = new Vector3f(thickness, thickness, length);
        Vector3f translation = new Vector3f(-thickness/2f, -thickness/2f, -length/2f);

        Transformation transform = new Transformation(
                translation,
                rotation,
                scale,
                new Quaternionf()
        );

        display.setTransformation(transform);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);

        return new DisplayEntity(display);
    }

    private Material getColorMaterial(Color color) {
        if (color.equals(Color.RED)) return Material.RED_CONCRETE;
        if (color.equals(Color.BLUE)) return Material.BLUE_CONCRETE;
        if (color.equals(Color.GREEN)) return Material.LIME_CONCRETE;
        return Material.WHITE_CONCRETE;
    }

    // ... остальной код класса без изменений ...
    // (copy-paste методы ensureChunkLoaded, spawnBlockDisplay, spawnItemDisplay, spawnArmorStand, applyCommonDisplayTraits)

    private void ensureChunkLoaded(World world, Location loc) {
        int x = loc.getBlockX() >> 4;
        int z = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(x, z)) {
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