package com.ladakx.inertia.features.commands.impl;

import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.api.world.IPhysicsWorld;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.features.commands.CloudModule;
import com.ladakx.inertia.physics.world.PhysicsWorldRegistry;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.BooleanParser;

import com.ladakx.inertia.physics.world.managers.PhysicsTaskManager;

import java.util.Locale;

public class AdminCommands extends CloudModule {

    private final PhysicsWorldRegistry worldRegistry;
    private final PhysicsMetricsService metricsService;

    public AdminCommands(CommandManager<CommandSender> manager,
                         ConfigurationService config,
                         PhysicsWorldRegistry worldRegistry,
                         PhysicsMetricsService metricsService) {
        super(manager, config);
        this.worldRegistry = worldRegistry;
        this.metricsService = metricsService;
    }

    @Override
    public void register() {
        var adminRoot = rootBuilder().literal("admin").permission("inertia.command.admin");

        // Command: /inertia admin pause [true/false]
        manager.command(adminRoot
                .literal("pause")
                .permission("inertia.admin.pause")
                .optional("state", BooleanParser.booleanParser())
                .handler(ctx -> {
                    boolean state;
                    // Если аргумент не указан, переключаем состояние первого найденного мира (упрощение для UX)
                    // или берем глобальный флаг, если бы он был.
                    // В текущей архитектуре пауза локальна для мира, но команда admin pause обычно глобальная.
                    // Реализуем логику: если не указано, то тогл, если указано - сет.
                    
                    // Для простоты возьмем состояние первого мира, чтобы определить текущий тогл
                    boolean isAnyPaused = worldRegistry.getAllSpaces().stream()
                            .anyMatch(IPhysicsWorld::isSimulationPaused);
                    
                    if (ctx.contains("state")) {
                        state = ctx.get("state");
                    } else {
                        state = !isAnyPaused;
                    }

                    int count = 0;
                    for (IPhysicsWorld world : worldRegistry.getAllSpaces()) {
                        world.setSimulationPaused(state);
                        count++;
                    }

                    send(ctx.sender(), MessageKey.ADMIN_SIMULATION_PAUSED, 
                            "{state}", state ? "PAUSED" : "RESUMED", 
                            "{count}", String.valueOf(count));
                })
        );

        // Command: /inertia admin stats
        manager.command(adminRoot
                .literal("stats")
                .permission("inertia.admin.stats")
                .handler(ctx -> {
                    double avg1s = metricsService.getAverageMspt1s();
                    double avg5s = metricsService.getAverageMspt5s();
                    double avg1m = metricsService.getAverageMspt1m();
                    double peak1s = metricsService.getPeakMspt1s();
                    
                    int activeBodies = metricsService.getActiveBodyCount();
                    int staticBodies = metricsService.getStaticBodyCount();
                    
                    // Форматируем числа
                    String fmtAvg = String.format(Locale.ROOT, "%.2f / %.2f / %.2f", avg1s, avg5s, avg1m);
                    String fmtPeak = String.format(Locale.ROOT, "%.2f", peak1s);
                    String recurringByCategory = String.format(Locale.ROOT, "C:%.2f / N:%.2f / B:%.2f",
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.CRITICAL),
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.NORMAL),
                            metricsService.getRecurringExecutionMs(PhysicsTaskManager.RecurringTaskPriority.BACKGROUND)
                    );
                    String skippedByCategory = String.format(Locale.ROOT, "C:%d / N:%d / B:%d",
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.CRITICAL),
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.NORMAL),
                            metricsService.getRecurringSkipped(PhysicsTaskManager.RecurringTaskPriority.BACKGROUND)
                    );

                    send(ctx.sender(), MessageKey.ADMIN_STATS_HEADER);
                    send(ctx.sender(), MessageKey.ADMIN_STATS_PERFORMANCE, "{avg}", fmtAvg, "{peak}", fmtPeak);
                    send(ctx.sender(), MessageKey.ADMIN_STATS_BODIES, "{dynamic}", String.valueOf(activeBodies), "{static}", String.valueOf(staticBodies));
                    send(ctx.sender(), MessageKey.ADMIN_STATS_TASKS,
                            "{oneTime}", String.format(Locale.ROOT, "%.2f", metricsService.getOneTimeExecutionMs()),
                            "{recurringTotal}", String.format(Locale.ROOT, "%.2f", metricsService.getRecurringExecutionMsTotal()),
                            "{recurringByCategory}", recurringByCategory,
                            "{skipped}", skippedByCategory,
                            "{queueOneTime}", String.valueOf(metricsService.getOneTimeQueueDepth()),
                            "{queueRecurring}", String.valueOf(metricsService.getRecurringQueueDepth())
                    );
                    send(ctx.sender(), MessageKey.ADMIN_STATS_FOOTER);
                })
        );
    }
}