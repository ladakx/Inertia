package com.ladakx.inertia.render;

import com.ladakx.inertia.InertiaLogger;
import com.ladakx.inertia.render.config.RenderEntityDefinition;
import com.ladakx.inertia.render.config.RenderModelDefinition;
import com.ladakx.inertia.render.runtime.PhysicsDisplayComposite;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.RVec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Фабрика, яка за RenderModelDefinition будує конкретні Bukkit-entity.
 */
public final class DisplayEntityFactory {

    private final ItemModelResolver itemModelResolver;

    public DisplayEntityFactory(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = Objects.requireNonNull(itemModelResolver, "itemModelResolver");
    }

    public PhysicsDisplayComposite createComposite(RenderModelDefinition model,
                                                   World world,
                                                   Body body) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(body, "body");

        List<PhysicsDisplayComposite.DisplayPart> parts = new ArrayList<>();

        RVec3 pos = body.getPosition();
        Location origin = new Location(world, pos.xx(), pos.yy(), pos.zz());

        for (RenderEntityDefinition def : model.entities().values()) {
            Entity entity = spawnEntity(world, origin, def);
            if (entity != null) {
                parts.add(new PhysicsDisplayComposite.DisplayPart(def, entity));
            }
        }

        return new PhysicsDisplayComposite(body, model, parts);
    }

    private Entity spawnEntity(World world,
                               Location origin,
                               RenderEntityDefinition def) {
        return switch (def.kind()) {
            case ARMOR_STAND -> spawnArmorStand(world, origin, def);
            case ITEM_DISPLAY -> spawnItemDisplay(world, origin, def);
            case BLOCK_DISPLAY -> spawnBlockDisplay(world, origin, def);
        };
    }

    private ArmorStand spawnArmorStand(World world,
                                       Location origin,
                                       RenderEntityDefinition def) {
        return world.spawn(origin, ArmorStand.class, stand -> {
            stand.setGravity(false);
            stand.setMarker(def.marker());
            stand.setInvisible(def.invisible());
            stand.setSmall(def.small());
            stand.setBasePlate(def.basePlate());
            stand.setArms(def.arms());
            stand.setPersistent(false);
        });
    }

    private ItemDisplay spawnItemDisplay(World world,
                                         Location origin,
                                         RenderEntityDefinition def) {
        return world.spawn(origin, ItemDisplay.class, display -> {
            display.setPersistent(false);

            if (def.itemModelKey() != null) {
                ItemStack stack = itemModelResolver.resolve(def.itemModelKey());
                if (stack == null) {
                    InertiaLogger.warn("No item found for item-model '" + def.itemModelKey()
                            + "', using BARRIER");
                    stack = new ItemStack(org.bukkit.Material.BARRIER);
                }
                display.setItemStack(stack);
            }

            if (def.displayModeRaw() != null) {
                try {
                    ItemDisplay.ItemDisplayTransform transform =
                            ItemDisplay.ItemDisplayTransform.valueOf(
                                    def.displayModeRaw().toUpperCase(Locale.ROOT));
                    display.setItemDisplayTransform(transform);
                } catch (IllegalArgumentException ex) {
                    InertiaLogger.warn("Invalid display-mode '" + def.displayModeRaw()
                            + "' for item display '" + def.key() + "'");
                }
            }

            configureCommonDisplay(display, def);
        });
    }

    private BlockDisplay spawnBlockDisplay(World world,
                                           Location origin,
                                           RenderEntityDefinition def) {
        return world.spawn(origin, BlockDisplay.class, display -> {
            display.setPersistent(false);

            if (def.blockType() != null) {
                BlockData data = def.blockType().createBlockData();
                display.setBlock(data);
            } else {
                InertiaLogger.warn("BlockDisplay '" + def.key()
                        + "' has no 'block' defined, using STONE");
                display.setBlock(org.bukkit.Material.STONE.createBlockData());
            }

            configureCommonDisplay(display, def);
        });
    }

    private void configureCommonDisplay(Display display, RenderEntityDefinition def) {
        if (def.viewRange() != null) {
            display.setViewRange(def.viewRange());
        }
        if (def.shadowRadius() != null) {
            display.setShadowRadius(def.shadowRadius());
        }
        if (def.shadowStrength() != null) {
            display.setShadowStrength(def.shadowStrength());
        }
        if (def.teleportDuration() != null) {
            display.setTeleportDuration(def.teleportDuration());
        }
        if (def.billboard() != null) {
            display.setBillboard(def.billboard());
        }
        if (def.brightnessBlock() != null || def.brightnessSky() != null) {
            int block = def.brightnessBlock() != null ? def.brightnessBlock() : 0;
            int sky = def.brightnessSky() != null ? def.brightnessSky() : 0;
            Display.Brightness brightness = new Display.Brightness(block, sky);
            display.setBrightness(brightness);
        }
        // scale/translation будуть застосовуватись в PhysicsDisplayComposite через teleport + rotation;
        // якщо захочеш - можна додатково використовувати Transformation.
    }
}