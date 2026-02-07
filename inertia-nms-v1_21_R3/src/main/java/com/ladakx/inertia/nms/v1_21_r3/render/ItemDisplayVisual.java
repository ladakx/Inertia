package com.ladakx.inertia.nms.v1_21_r3.render;

import com.ladakx.inertia.nms.PacketFactory;
import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import com.ladakx.inertia.rendering.config.enums.InertiaDisplayMode;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDisplayVisual extends AbstractNetworkVisual {

    private final ItemModelResolver itemResolver;

    public ItemDisplayVisual(RenderEntityDefinition definition, PacketFactory packetFactory, ItemModelResolver itemResolver) {
        super(definition, packetFactory);
        this.itemResolver = itemResolver;
    }

    @Override
    protected void addTypeSpecificMetadata(List<SynchedEntityData.DataValue<?>> data) {
        ItemStack itemStack;
        if (definition.itemModelKey() != null) {
            itemStack = itemResolver.resolve(definition.itemModelKey());
        } else {
            itemStack = new ItemStack(Material.STONE);
        }
        
        if (itemStack == null) itemStack = new ItemStack(Material.BARRIER);

        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        
        data.add(SynchedEntityData.DataValue.create(Display.ItemDisplay.DATA_ITEM_STACK_ID, nmsItem));
        
        // Display Context (Transform Type)
        ItemDisplayContext context = convertDisplayMode(definition.displayMode());
        data.add(SynchedEntityData.DataValue.create(Display.ItemDisplay.DATA_ITEM_DISPLAY_CONTEXT_ID, (byte) context.ordinal()));
    }
    
    private ItemDisplayContext convertDisplayMode(InertiaDisplayMode mode) {
        if (mode == null) return ItemDisplayContext.NONE;
        return switch (mode) {
            case THIRDPERSON_LEFTHAND -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            case THIRDPERSON_RIGHTHAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case FIRSTPERSON_LEFTHAND -> ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            case FIRSTPERSON_RIGHTHAND -> ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            case HEAD -> ItemDisplayContext.HEAD;
            case GUI -> ItemDisplayContext.GUI;
            case GROUND -> ItemDisplayContext.GROUND;
            case FIXED -> ItemDisplayContext.FIXED;
            default -> ItemDisplayContext.NONE;
        };
    }
}