package com.ladakx.inertia.bullet.block;

import com.jme3.bounding.BoundingBox;
import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.serializers.BoundingSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the settings of a block.
 * @param boxList the list of bounding boxes.
 * @param friction the friction of the block.
 * @param restitution the restitution of the block.
 * @param collidable if the block is collidable.
 * @param cullFaces if the block culls faces.
 * @param isFullBlock  if the block is a full block.
 * @param isSlab if the block is a slab.
 */
public record BulletBlockSettings(List<BoundingBox> boxList, float friction, float restitution, boolean collidable, boolean cullFaces, boolean isFullBlock, boolean isSlab) {

    @NotNull
    public static BulletBlockSettings getBlockSettings(Material material, FileConfiguration cfg) {
        String path = material.name();
        List<BoundingBox> boxes = BoundingSerializer.parseListFromStrings(cfg.getStringList(path+".BoundingBox"));

        boolean isSlab = false;
        if (boxes.size() == 1) {
            isSlab = boxes.get(0).isSlab();
        } else if (boxes.isEmpty()) {
            if (material.isBlock()) {
                if (material.createBlockData() instanceof org.bukkit.block.data.type.Slab) {
                    isSlab = true;
                }
            }
        }

        return new BulletBlockSettings(
                boxes,
                (float) Math.max(cfg.getDouble(path+".Friction", cfg.getDouble("DEFAULT.Friction", 0.75F)), 0.0f),
                (float) Math.max(cfg.getDouble(path+".Restitution", cfg.getDouble("DEFAULT.Friction", 0.75F)), 0.0f),
                cfg.getBoolean(path+".Collidable", cfg.getBoolean("DEFAULT.Collidable", true)),
                cfg.getBoolean(path+".Full_Block", cfg.getBoolean("DEFAULT.Full_Block", true)),
                cfg.getBoolean(path+".Cull_Faces", cfg.getBoolean("DEFAULT.Cull_Faces", true)),
                isSlab
        );
    }

    @NotNull
    public static BulletBlockSettings getBlockSettings(Block block) {
        return getBlockSettings(block.getType());
    }

    @NotNull
    public static BulletBlockSettings getBlockSettings(Material material) {
        var settings = InertiaPlugin.getBlocksConfig();
        return settings.properties[material.ordinal()];
    }
}