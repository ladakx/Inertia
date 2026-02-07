package com.ladakx.inertia.features.tools;

import com.ladakx.inertia.physics.body.impl.AbstractPhysicsBody;
import org.bukkit.entity.Player;

public interface NetworkInteractTool {
    void onNetworkInteract(Player player, AbstractPhysicsBody body, boolean attack);
}
