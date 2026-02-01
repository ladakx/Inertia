package com.ladakx.inertia.features.commands;

import com.ladakx.inertia.common.logging.InertiaLogger;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import com.ladakx.inertia.features.commands.impl.ManageCommands;
import com.ladakx.inertia.features.commands.impl.SpawnCommands;
import com.ladakx.inertia.features.commands.impl.SystemCommands;
import com.ladakx.inertia.features.commands.impl.ToolCommands;
import com.ladakx.inertia.features.tools.ToolRegistry;
import com.ladakx.inertia.physics.factory.BodyFactory;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.AudienceProvider;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.util.HashMap;
import java.util.Map;

public class InertiaCommandManager {

    private final InertiaPlugin plugin;
    private final ConfigurationService configService;
    private LegacyPaperCommandManager<CommandSender> commandManager;
    private MinecraftHelp<CommandSender> minecraftHelp;

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

            // Check if we are using Native Brigadier (Paper 1.20.6+)
            boolean hasNativeBrigadier = commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER);

            if (hasNativeBrigadier) {
                // Modern Paper: Just register Brigadier.
                // DO NOT register AsyncCompletions, as Brigadier handles suggestions natively.
                commandManager.registerBrigadier();
            } else if (commandManager.hasCapability(CloudBukkitCapabilities.COMMODORE_BRIGADIER)) {
                // Legacy Spigot/Paper: Register Commodore
                commandManager.registerBrigadier();
            }

            // Only register Async Completions if we are NOT using Native Brigadier.
            // The AsyncCommandSuggestionListener crashes when cast against ModernPaperBrigadier.
            if (!hasNativeBrigadier && commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                commandManager.registerAsynchronousCompletions();
            }

            setupExceptionHandling();
            setupHelp();

        } catch (Exception e) {
            InertiaLogger.error("Failed to initialize Cloud Command Manager", e);
        }
    }

    private void setupExceptionHandling() {
        MinecraftExceptionHandler.<CommandSender>create(sender -> (net.kyori.adventure.audience.Audience) sender)
                .defaultHandlers()
                .decorator(
                        component -> Component.text()
                                .append(Component.text("[Inertia] ", NamedTextColor.RED))
                                .append(component)
                                .build()
                )
                .registerTo(commandManager);
    }

    private void setupHelp() {
        var colors = MinecraftHelp.helpColors(
                NamedTextColor.RED,
                NamedTextColor.WHITE,
                NamedTextColor.GOLD,
                NamedTextColor.GRAY,
                NamedTextColor.DARK_GRAY
        );

        this.minecraftHelp = MinecraftHelp.<CommandSender>builder()
                .commandManager(commandManager)
                .audienceProvider(sender -> (net.kyori.adventure.audience.Audience) sender)
                .commandPrefix("/inertia help")
                .colors(colors)
                .messageProvider((sender, key, args) -> {
                    MessageKey targetKey = null;

                    if (key.contains("help_title")) targetKey = MessageKey.HELP_PAGE_HEADER;
                    else if (key.contains("click_for_next_page")) targetKey = MessageKey.HELP_NEXT_PAGE;
                    else if (key.contains("click_for_previous_page")) targetKey = MessageKey.HELP_PREV_PAGE;
                    else if (key.contains("page_out_of_range")) targetKey = MessageKey.HELP_PAGE_HEADER;

                    if (targetKey != null) {
                        Component comp = configService.getMessageManager().getSingle(targetKey);
                        for (Map.Entry<String, String> entry : args.entrySet()) {
                            String placeholder = "{" + entry.getKey() + "}";
                            String value = entry.getValue();
                            comp = comp.replaceText(config -> config.matchLiteral(placeholder).replacement(value));
                        }
                        return comp;
                    }

                    return Component.text(key, colors.text());
                })
                .build();
    }

    public void registerCommands(ConfigurationService config,
                                 BodyFactory bodyFactory,
                                 ToolRegistry toolRegistry,
                                 PhysicsWorldRegistry worldRegistry) {

        new SystemCommands(commandManager, config, minecraftHelp).register();
        new SpawnCommands(commandManager, config, bodyFactory).register();
        new ToolCommands(commandManager, config, toolRegistry).register();
        new ManageCommands(commandManager, config, worldRegistry).register();
    }
}