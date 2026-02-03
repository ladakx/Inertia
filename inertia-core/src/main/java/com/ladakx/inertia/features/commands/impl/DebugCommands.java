package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.api.service.DebugRenderService;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.CloudModule;
import com.ladakx.inertia.features.tools.Tool;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.features.ui.BossBarPerformanceMonitor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.IntegerParser;

public class DebugCommands extends CloudModule {

    private final DebugRenderService debugRenderService;
    private final BossBarPerformanceMonitor perfMonitor;
    private final ToolRegistry toolRegistry;

    public DebugCommands(CommandManager<CommandSender> manager,
                         ConfigurationService config,
                         DebugRenderService debugRenderService,
                         BossBarPerformanceMonitor perfMonitor,
                         ToolRegistry toolRegistry) {
        super(manager, config);
        this.debugRenderService = debugRenderService;
        this.perfMonitor = perfMonitor;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void register() {
        var debugRoot = rootBuilder().literal("debug").permission("inertia.command.debug");

        // Command: /inertia debug hitboxes [range]
        manager.command(debugRoot
                .literal("hitboxes")
                .permission("inertia.debug.hitboxes")
                .optional("range", IntegerParser.integerParser(1, 100))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    
                    Integer range = ctx.getOrDefault("range", null);
                    debugRenderService.toggleDebug(player, range);
                    
                    boolean state = debugRenderService.isDebugEnabled(player);
                    String stateStr = state ? "&aEnabled" : "&cDisabled";
                    String rangeStr = state ? " &7(Range: " + (range != null ? range : "Default") + ")" : "";
                    
                    send(player, MessageKey.DEBUG_HITBOX_TOGGLE, "{state}", stateStr + rangeStr);
                })
        );

        // Command: /inertia debug perf
        manager.command(debugRoot
                .literal("perf")
                .permission("inertia.debug.perf")
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    perfMonitor.toggle(player);
                    // Сообщение отправляется внутри метода toggle, так как там есть логика состояния
                })
        );

        // Command: /inertia debug inspect
        manager.command(debugRoot
                .literal("inspect")
                .permission("inertia.debug.inspect")
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    if (!validateWorld(player)) return;

                    Tool tool = toolRegistry.getTool("inspector");
                    if (tool != null) {
                        player.getInventory().addItem(tool.buildItem());
                        send(player, MessageKey.TOOL_RECEIVED, "{tool}", "Inspector");
                    } else {
                        send(player, MessageKey.TOOL_NOT_FOUND);
                    }
                })
        );
    }
}