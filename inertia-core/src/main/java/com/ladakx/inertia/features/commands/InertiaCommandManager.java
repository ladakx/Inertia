package com.ladakx.inertia.features.commands;

import com.ladakx.inertia.api.service.DebugRenderService;
import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.features.commands.impl.*;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.features.ui.BossBarPerformanceMonitor;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public class InertiaCommandManager {

    private final InertiaPlugin plugin;
    private final ConfigurationService configService;
    private LegacyPaperCommandManager<CommandSender> commandManager;

    public InertiaCommandManager(InertiaPlugin plugin, ConfigurationService configService) {
        this.plugin = plugin;
        this.configService = configService;
        init();
    }

    private void init() {
        try {
            ExecutionCoordinator<CommandSender> coordinator = ExecutionCoordinator.simpleCoordinator();
            this.commandManager = LegacyPaperCommandManager.createNative(
                    plugin,
                    coordinator
            );

            boolean hasNativeBrigadier = commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER);
            if (hasNativeBrigadier) {
                commandManager.registerBrigadier();
            } else if (commandManager.hasCapability(CloudBukkitCapabilities.COMMODORE_BRIGADIER)) {
                commandManager.registerBrigadier();
            }

            if (!hasNativeBrigadier && commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                commandManager.registerAsynchronousCompletions();
            }

            setupExceptionHandling();

        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize Cloud Command Manager", e);
        }
    }

    private void setupExceptionHandling() {
        MinecraftExceptionHandler.<CommandSender>create(sender -> sender)
                .defaultHandlers()
                .decorator(
                        component -> Component.text()
                                .append(Component.text("[Inertia] ", NamedTextColor.RED))
                                .append(component)
                                .build()
                )
                .registerTo(commandManager);
    }

    public void registerCommands(ConfigurationService config,
                                 BodyFactory bodyFactory,
                                 ToolRegistry toolRegistry,
                                 PhysicsWorldRegistry worldRegistry,
                                 PhysicsMetricsService metricsService,
                                 DebugRenderService debugService,
                                 BossBarPerformanceMonitor perfMonitor) {
        new SystemCommands(commandManager, config).register();
        new SpawnCommands(commandManager, config, bodyFactory).register();
        new ToolCommands(commandManager, config, toolRegistry).register();
        new ManageCommands(commandManager, config, worldRegistry).register();
        new AdminCommands(commandManager, config, worldRegistry, metricsService).register();
        new DebugCommands(commandManager, config, debugService, perfMonitor, toolRegistry).register();
    }
}
