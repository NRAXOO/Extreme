package com.extreme.pvp;

import com.extreme.ExtremePlugin;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estado del PVP y su modo aleatorio. No hay tareas repetitivas: solo UN temporizador pendiente
 * hasta el próximo cambio. El PVP se aplica con World#setPVP, sin escuchar daños.
 */
public class PVPManager {

    private final ExtremePlugin plugin;
    private BukkitTask randomTask;

    public PVPManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get("pvp.yml");
    }

    public void reload() {
        applyToWorlds();
        scheduleRandom(false);
    }

    public void shutdown() {
        if (randomTask != null) randomTask.cancel();
        randomTask = null;
    }

    public boolean isEnabled() {
        return cfg().getBoolean("pvp.enabled", false);
    }

    public boolean isRandomEnabled() {
        return cfg().getBoolean("pvp.random.enabled", false);
    }

    /**
     * Cambia el estado del PVP.
     * @param manual true si lo hizo un administrador (prioridad sobre el sistema automático)
     * @return false si ya estaba en ese estado
     */
    public boolean set(boolean on, boolean manual) {
        if (on == isEnabled()) return false;
        plugin.configs().set("pvp.yml", "pvp.enabled", on);
        applyToWorlds();
        plugin.messages().broadcast(on ? "pvp-enabled" : "pvp-disabled");
        plugin.sounds().playAll(on ? "pvp-enabled" : "pvp-disabled");
        if (manual && isRandomEnabled() && cfg().getBoolean("pvp.random.manual-resets-timer", true)) {
            scheduleRandom(true);
        }
        return true;
    }

    public void setRandom(boolean enabled) {
        plugin.configs().set("pvp.yml", "pvp.random.enabled", enabled);
        if (enabled) {
            scheduleRandom(true);
        } else {
            shutdown();
            plugin.state().nextPvpToggle = 0L;
            plugin.state().save();
        }
    }

    /** Aplica el estado actual a los mundos configurados. */
    public void applyToWorlds() {
        boolean on = isEnabled();
        List<String> names = cfg().getStringList("pvp.worlds");
        for (World w : Bukkit.getWorlds()) {
            if (names.isEmpty() || names.contains(w.getName())) w.setPVP(on);
        }
    }

    public void applyToWorld(World w) {
        List<String> names = cfg().getStringList("pvp.worlds");
        if (names.isEmpty() || names.contains(w.getName())) w.setPVP(isEnabled());
    }

    /**
     * @param fresh true = ignora el temporizador guardado y sortea uno nuevo
     */
    public void scheduleRandom(boolean fresh) {
        shutdown();
        if (!isRandomEnabled()) return;

        long now = System.currentTimeMillis();
        long target = plugin.state().nextPvpToggle;
        if (fresh || target <= 0L) target = now + randomDelay();
        plugin.state().nextPvpToggle = target;
        plugin.state().save();

        long ticks = Math.max(1L, (target - now) / 50L);
        randomTask = Bukkit.getScheduler().runTaskLater(plugin, new PVPRandomTask(this), ticks);
    }

    private long randomDelay() {
        long min = TimeUtil.parse(cfg().getString("pvp.random.min-time"), 1_800_000L);
        long max = TimeUtil.parse(cfg().getString("pvp.random.max-time"), 7_200_000L);
        if (max < min) {
            long t = min;
            min = max;
            max = t;
        }
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /** Llamado por PVPRandomTask cuando vence el temporizador. */
    void fireRandom() {
        if (!isRandomEnabled()) return;
        set(!isEnabled(), false);
        scheduleRandom(true);
    }

    /** Milisegundos hasta el próximo cambio aleatorio, o -1 si no hay temporizador. */
    public long remainingMs() {
        if (!isRandomEnabled()) return -1L;
        long t = plugin.state().nextPvpToggle;
        return t <= 0L ? -1L : Math.max(0L, t - System.currentTimeMillis());
    }

    public String timerText() {
        long r = remainingMs();
        return r < 0 ? plugin.configs().get("actionbar.yml").getString("actionbar.values.timer-none", "--:--")
                : TimeUtil.clock(r);
    }
}
