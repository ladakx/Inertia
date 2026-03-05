package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.api.InertiaApiAccess;
import com.ladakx.inertia.api.diagnostics.TransportDiagnosticsSnapshot;
import com.ladakx.inertia.api.diagnostics.TransportWorldSnapshot;
import com.ladakx.inertia.api.service.DebugRenderService;
import com.ladakx.inertia.api.transport.TransportHandle;
import com.ladakx.inertia.api.transport.TransportService;
import com.ladakx.inertia.api.transport.TransportState;
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
import org.incendo.cloud.parser.standard.StringParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        int maxRange = config.getInertiaConfig().GENERAL.DEBUG.hitboxMaxRange;
        int defaultRange = config.getInertiaConfig().GENERAL.DEBUG.hitboxDefaultRange;

        // Command: /inertia debug hitboxes [range]
        manager.command(debugRoot
                .literal("hitboxes")
                .permission("inertia.debug.hitboxes")
                .optional("range", IntegerParser.integerParser(1, Math.max(1, maxRange)))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    
                    Integer range = ctx.getOrDefault("range", null);
                    int resolvedRange = debugRenderService.toggleDebug(player, range != null ? range : defaultRange);
                    
                    boolean state = debugRenderService.isDebugEnabled(player);
                    String stateStr = state ? "&aEnabled" : "&cDisabled";
                    String rangeStr = state ? " &7(Range: " + resolvedRange + ")" : "";
                    
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

        manager.command(debugRoot
                .literal("transport")
                .literal("metrics")
                .permission("inertia.debug.transport")
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();

                    TransportDiagnosticsSnapshot snapshot = InertiaApiAccess.resolve()
                            .diagnostics()
                            .getTransportDiagnosticsSnapshot();

                    player.sendMessage("§6[Inertia] §fTransport metrics");
                    player.sendMessage("§7Total: §f" + snapshot.totalTransports()
                            + " §8| §7Active: §f" + snapshot.activeTransports()
                            + " §8| §7Grounded: §f" + snapshot.groundedTransports());
                    player.sendMessage("§7Avg km/h: §f" + format2(snapshot.averageSpeedKmh())
                            + " §8| §7Max km/h: §f" + format2(snapshot.maxSpeedKmh()));
                    for (TransportWorldSnapshot world : snapshot.worldSnapshots()) {
                        player.sendMessage("§8- §f" + world.worldName()
                                + " §7count=§f" + world.transports()
                                + " §7active=§f" + world.activeTransports()
                                + " §7avg=§f" + format2(world.averageSpeedKmh())
                                + " §7max=§f" + format2(world.maxSpeedKmh())
                                + " §7rpm=§f" + format2(world.averageEngineRpm()));
                    }
                })
        );

        manager.command(debugRoot
                .literal("transport")
                .literal("list")
                .permission("inertia.debug.transport")
                .optional("limit", IntegerParser.integerParser(1, 50))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    int limit = ctx.getOrDefault("limit", 10);

                    TransportService transport = InertiaApiAccess.resolve().transport();
                    List<TransportHandle> handles = new ArrayList<>(transport.getAll());
                    handles.sort((a, b) -> a.id().value().compareToIgnoreCase(b.id().value()));

                    player.sendMessage("§6[Inertia] §fTransports: §e" + handles.size() + "§f (showing up to " + limit + ")");
                    int shown = 0;
                    for (TransportHandle handle : handles) {
                        if (shown >= limit) break;
                        TransportState state = transport.getState(handle.id());
                        String status = state == null ? "n/a" : (state.grounded() ? "grounded" : "air");
                        String speed = state == null ? "n/a" : format2(state.speedKmh());
                        String rpm = state == null ? "n/a" : format2(state.engineRpm());
                        player.sendMessage("§8- §f" + handle.id().value()
                                + " §7type=§f" + handle.type()
                                + " §7owner=§f" + handle.owner().pluginName()
                                + " §7km/h=§f" + speed
                                + " §7rpm=§f" + rpm
                                + " §7state=§f" + status);
                        shown++;
                    }
                })
        );

        manager.command(debugRoot
                .literal("transport")
                .literal("inspect")
                .permission("inertia.debug.transport")
                .required("id", StringParser.stringParser(),
                        org.incendo.cloud.suggestion.SuggestionProvider.blockingStrings((c, i) ->
                                InertiaApiAccess.resolve().transport().getAll().stream()
                                        .map(h -> h.id().value())
                                        .sorted()
                                        .toList()))
                .handler(ctx -> {
                    if (!validatePlayer(ctx.sender())) return;
                    Player player = (Player) ctx.sender();
                    String id = ctx.get("id");

                    TransportService transport = InertiaApiAccess.resolve().transport();
                    TransportHandle handle = transport.get(new com.ladakx.inertia.api.transport.TransportId(id));
                    if (handle == null) {
                        player.sendMessage("§c[Inertia] Transport not found: " + id);
                        return;
                    }
                    TransportState state = transport.getState(handle.id());

                    player.sendMessage("§6[Inertia] §fTransport inspect");
                    player.sendMessage("§7ID: §f" + handle.id().value());
                    player.sendMessage("§7Type: §f" + handle.type() + " §8| §7Owner: §f" + handle.owner().pluginName());
                    if (state != null) {
                        player.sendMessage("§7Pos: §f" + format2(state.location().getX()) + ", " + format2(state.location().getY()) + ", " + format2(state.location().getZ()));
                        player.sendMessage("§7Speed km/h: §f" + format2(state.speedKmh())
                                + " §8| §7RPM: §f" + format2(state.engineRpm())
                                + " §8| §7Gear: §f" + state.currentGear()
                                + " §8| §7Grounded: §f" + state.grounded());
                        player.sendMessage("§7Input: §ff=" + format2(state.input().forward())
                                + " r=" + format2(state.input().right())
                                + " b=" + format2(state.input().brake())
                                + " hb=" + format2(state.input().handBrake())
                                + " clutch=" + format2(state.input().clutch())
                                + " gear=" + (state.input().manualGear() == null ? "auto" : state.input().manualGear()));
                        if (state.trackedInput() != null) {
                            player.sendMessage("§7TrackedInput: §ff=" + format2(state.trackedInput().forward())
                                    + " l=" + format2(state.trackedInput().leftRatio())
                                    + " r=" + format2(state.trackedInput().rightRatio())
                                    + " b=" + format2(state.trackedInput().brake()));
                        }
                    } else {
                        player.sendMessage("§cState is unavailable.");
                    }
                    if (!handle.customData().isEmpty()) {
                        player.sendMessage("§7CustomData: §f" + handle.customData());
                    }
                })
        );
    }

    private static String format2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
