package com.ladakx.inertia.nms.v1_21_r8.render;

import com.ladakx.inertia.infrastructure.nms.packet.PacketFactory;
import com.ladakx.inertia.nms.v1_21_r8.packet.PacketFactoryImpl;
import com.ladakx.inertia.rendering.ItemModelResolver;
import com.ladakx.inertia.rendering.NetworkVisual;
import com.ladakx.inertia.rendering.RenderFactory;
import com.ladakx.inertia.rendering.config.RenderEntityDefinition;
import org.bukkit.Location;
import org.bukkit.World;

public class RenderFactoryImpl implements RenderFactory {

    private final ItemModelResolver itemResolver;
    private final PacketFactory packetFactory;

    public RenderFactoryImpl(ItemModelResolver itemResolver) {
        this.itemResolver = itemResolver;
        this.packetFactory = new PacketFactoryImpl();
    }

    @Override
    public NetworkVisual create(World world, Location origin, RenderEntityDefinition definition) {
        switch (definition.kind()) {
            case BLOCK_DISPLAY:
                return new BlockDisplayVisual(definition, packetFactory);
            case ITEM_DISPLAY:
                return new ItemDisplayVisual(definition, packetFactory, itemResolver);
            case ARMOR_STAND:
                return new ItemDisplayVisual(definition, packetFactory, itemResolver); 
            case BOAT:
                return new BoatVisual(definition, packetFactory);
            case SHULKER:
                return new ShulkerVisual(definition, packetFactory);
            case INTERACTION:
                return new InteractionVisual(definition, packetFactory);
            default:
                throw new IllegalArgumentException("Unsupported visual kind: " + definition.kind());
        }
    }
}
