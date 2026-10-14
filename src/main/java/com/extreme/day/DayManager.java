package com.extreme.day;

import com.extreme.ExtremePlugin;
import com.extreme.config.StateStore;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Contador de días del servidor. El día se calcula por tiempo real transcurrido desde state.dayStart,
 * así que sobrevive a reinicios. Solo hay UNA tarea programada: la que despierta en el próximo cambio de día.
 */
public class DayManager {

    private final ExtremePlugin plugin;
    private BukkitTask task;
    private long dayMs = 86_400_000L;
    private int maxDays = 15;

    public DayManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("config.yml");
        dayMs = Math.max(1000L, TimeUtil.parse(c.getString("server.day-duration"), 86_400_000L));
        maxDays = Math.max(1, c.getInt("server.max-days", 15));
        int d = getDay();
        if (plugin.state().lastDay != d) announceChange(d);
        schedule();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public int getDay() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - plugin.state().dayStart);
        long d = elapsed / dayMs + 1;
        return (int) Math.min(d, maxDays);
    }

    /** Día real transcurrido SIN el tope de max-days (para la Pesadilla, que puede crecer sin límite). */
    public int getRawDay() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - plugin.state().dayStart);
        return (int) Math.min(Integer.MAX_VALUE - 1, elapsed / dayMs + 1);
    }

    public int getMaxDays() { return maxDays; }

    public long getDayMs() { return dayMs; }

    /** Cambia el día actual (1..max). Con announce=false no se emiten mensajes. */
    public void setDay(int day, boolean announce) {
        int target = Math.max(1, Math.min(day, maxDays));
        StateStore s = plugin.state();
        s.dayStart = System.currentTimeMillis() - (target - 1L) * dayMs;
        s.maxAnnounced = false;
        if (announce) {
            s.lastDay = 0; // fuerza el anuncio
            announceChange(target);
        } else {
            s.lastDay = target;
            if (target >= maxDays) s.maxAnnounced = true;
            s.save();
        }
        schedule();
    }

    private void announceChange(int day) {
        StateStore s = plugin.state();
        if (s.lastDay == day) return;
        s.lastDay = day;

        plugin.messages().broadcast("day-changed", "day", day, "max_day", maxDays);
        plugin.sounds().playAll("day-changed");

        if (day >= maxDays && !s.maxAnnounced) {
            s.maxAnnounced = true;
            plugin.messages().broadcast("max-day-reached", "day", day, "max_day", maxDays);
            plugin.sounds().playAll("max-day-reached");
        }
        s.save();
    }

    private void schedule() {
        shutdown();
        if (getDay() >= maxDays) return;
        long elapsed = Math.max(0L, System.currentTimeMillis() - plugin.state().dayStart);
        long left = dayMs - (elapsed % dayMs);
        long ticks = Math.max(1L, left / 50L) + 1L;
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            announceChange(getDay());
            schedule();
        }, ticks);
    }
}
