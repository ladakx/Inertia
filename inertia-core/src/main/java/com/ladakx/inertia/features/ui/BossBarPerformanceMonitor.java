package com.ladakx.inertia.features.ui;

import com.ladakx.inertia.api.service.PhysicsMetricsService;
import com.ladakx.inertia.common.utils.StringUtils;
import com.ladakx.inertia.configuration.ConfigurationService;
import com.ladakx.inertia.configuration.message.MessageKey;
import com.ladakx.inertia.core.InertiaPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarPerformanceMonitor implements Listener {

    private final PhysicsMetricsService metricsService;
    private final ConfigurationService configService;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private BukkitTask updateTask;

    public BossBarPerformanceMonitor(InertiaPlugin plugin,
                                     PhysicsMetricsService metricsService,
                                     ConfigurationService configService) {
        this.metricsService = metricsService;
        this.configService = configService;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTask(plugin);
    }

    private void startTask(InertiaPlugin plugin) {
        this.updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateBars, 10L, 10L);
    }

    public void stop() {
        if (updateTask != null) updateTask.cancel();
        viewers.clear();
        bars.values().forEach(bar -> Bukkit.getOnlinePlayers().forEach(bar::removeViewer));
        bars.clear();
    }

    public void toggle(Player player) {
        if (viewers.contains(player.getUniqueId())) {
            removePlayer(player);
            configService.getMessageManager().send(player, MessageKey.DEBUG_PERF_TOGGLE, "{state}", "&cDisabled");
        } else {
            addPlayer(player);
            configService.getMessageManager().send(player, MessageKey.DEBUG_PERF_TOGGLE, "{state}", "&aEnabled");
        }
    }

    private void addPlayer(Player player) {
        viewers.add(player.getUniqueId());
        BossBar bar = BossBar.bossBar(Component.text("Loading..."), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        bars.put(player.getUniqueId(), bar);
        bar.addViewer(player);
    }

    private void removePlayer(Player player) {
        viewers.remove(player.getUniqueId());
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeViewer(player);
    }

    private void updateBars() {
        if (viewers.isEmpty()) return;

        double physMs = metricsService.getPhysicsMspt();
        double srvMs = metricsService.getServerMspt();

        // Расчет процента нагрузки (Physics / Server Time)
        double loadPerc = (srvMs > 0) ? (physMs / srvMs) * 100.0 : 0.0;

        int active = metricsService.getActiveBodyCount();
        int total = metricsService.getTotalBodyCount();
        int sleeping = total - active; // Примерный расчет
        int max = metricsService.getMaxBodyLimit();
        int staticBodies = metricsService.getStaticBodyCount(); // Пока 0, если не реализован точный подсчет

        // Форматирование: Server MSPT | Physics Step (%) | Bodies
        String srvStr = String.format(Locale.ROOT, "%.2f", srvMs);
        String physStr = String.format(Locale.ROOT, "%.2f", physMs);
        String percStr = String.format(Locale.ROOT, "%.1f", loadPerc);

        // Текст шаблона: "Server MSPT: {srv}ms | Physics: {phys}ms ({perc}%) | Bodies: {act}/{sleep}/{max} | Static: {stat}"
        // Мы используем MessageKey для шаблона
        Component text = configService.getMessageManager().getSingle(MessageKey.DEBUG_PERF_FORMAT);

        // Замена плейсхолдеров
        text = StringUtils.replace(text,
                "{srv}", srvStr,
                "{phys}", physStr,
                "{perc}", percStr,
                "{act}", String.valueOf(active),
                "{sleep}", String.valueOf(sleeping),
                "{max}", String.valueOf(max),
                "{stat}", String.valueOf(staticBodies)
        );

        // Цвет бара зависит от нагрузки физики относительно тика (50мс)
        float progress = (float) Math.min(1.0, physMs / 50.0);
        BossBar.Color color = (physMs < 10) ? BossBar.Color.GREEN : (physMs < 35 ? BossBar.Color.YELLOW : BossBar.Color.RED);

        for (BossBar bar : bars.values()) {
            bar.name(text);
            bar.progress(progress);
            bar.color(color);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }
}