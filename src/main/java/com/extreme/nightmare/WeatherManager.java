package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/** Clima extremo: tormentas más frecuentes y largas, rayos cerca de jugadores, tormentas ácidas y nieve ralentizadora. */
public class WeatherManager {

    private final ExtremePlugin plugin;
    private BukkitTask checkTask, stormTask;

    public WeatherManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void reload() {
        shutdown();
        YamlConfiguration c = cfg();
        if (!c.getBoolean("weather.enabled", true) || !plugin.modules().on("nm_weather") || !plugin.nightmare().isEnabled()) return;
        long check = Math.max(200L, c.getLong("weather.check-interval", 60) * 20L);
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::check, check, check);
        long storm = Math.max(40L, c.getLong("weather.storm-tick-interval", 100L));
        stormTask = Bukkit.getScheduler().runTaskTimer(plugin, this::stormTick, storm, storm);
    }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        if (stormTask != null) stormTask.cancel();
        checkTask = null;
        stormTask = null;
    }

    /** Sorteo de tormenta en mundos sin clima activo. */
    private void check() {
        YamlConfiguration c = cfg();
        if (!plugin.nightmare().isEnabled()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : Bukkit.getWorlds()) {
            if (!plugin.nightmare().worldEnabled(w) || w.getEnvironment() != World.Environment.NORMAL || w.getPlayers().isEmpty()) continue;
            if (w.hasStorm()) continue;
            double chance = c.getDouble("weather.storm-chance", 0.08) * plugin.nightmare().value("event-frequency");
            if (r.nextDouble() < Math.min(chance, 0.8)) beginStorm(w);
        }
    }

    public void beginStorm(World w) {
        YamlConfiguration c = cfg();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double base = r.nextDouble(c.getDouble("weather.min-duration", 300), c.getDouble("weather.max-duration", 900));
        int ticks = (int) (base * 20 * plugin.nightmare().value("event-duration"));
        w.setStorm(true);
        w.setWeatherDuration(ticks);
        if (r.nextDouble() < c.getDouble("weather.thunder-chance", 0.6)) {
            w.setThundering(true);
            w.setThunderDuration(ticks);
        }
        if (r.nextDouble() < c.getDouble("weather.acid-chance", 0.25)) plugin.acidRain().start(w, ticks / 20);
    }

    /** Llamado cuando el clima cambia por sí solo: alarga las tormentas. */
    public void onStormStart(World w) {
        if (!plugin.nightmare().worldEnabled(w) || !plugin.modules().on("nm_weather")) return;
        YamlConfiguration c = cfg();
        double mult = plugin.nightmare().value("event-duration");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!w.hasStorm()) return;
            w.setWeatherDuration((int) (w.getWeatherDuration() * Math.max(1.0, mult)));
            if (ThreadLocalRandom.current().nextDouble() < c.getDouble("weather.acid-chance", 0.25)) {
                plugin.acidRain().start(w, w.getWeatherDuration() / 20);
            }
        });
    }

    /** Rayos cerca de los jugadores durante tormentas eléctricas + nieve ralentizadora. */
    private void stormTick() {
        YamlConfiguration c = cfg();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : Bukkit.getWorlds()) {
            if (!w.hasStorm() || !plugin.nightmare().worldEnabled(w)) continue;
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                if (w.isThundering() && c.getBoolean("weather.lightning-near-players", true)
                        && r.nextDouble() < c.getDouble("weather.lightning-chance", 0.12) * plugin.nightmare().value("event-frequency")) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double d = r.nextDouble(c.getDouble("weather.lightning-min-distance", 6), c.getDouble("weather.lightning-max-distance", 20));
                    Location l = p.getLocation().add(Math.cos(ang) * d, 0, Math.sin(ang) * d);
                    l.setY(w.getHighestBlockYAt(l) + 1);
                    w.strikeLightning(l);
                }
                if (c.getBoolean("weather.snow-slowness", true) && p.getLocation().getBlock().getTemperature() < 0.15
                        && w.getHighestBlockYAt(p.getLocation()) <= p.getLocation().getBlockY()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, c.getInt("weather.snow-slowness-level", 0), true, true, true));
                }
            }
        }
    }

    /** Tras un rayo: probabilidad de que invoque criaturas cerca del jugador más próximo. */
    public void onLightning(Location loc) {
        YamlConfiguration c = cfg();
        if (!plugin.nightmare().worldEnabled(loc.getWorld()) || !plugin.modules().on("nm_weather")) return;
        if (ThreadLocalRandom.current().nextDouble() >= c.getDouble("weather.lightning-event-chance", 0.2)) return;
        Player best = null;
        double bd = 40 * 40;
        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best == null) return;
        int n = (int) Math.max(2, Math.round(c.getInt("weather.lightning-mobs", 4) * plugin.nightmare().value("mob-count")));
        plugin.hordes().start(best, java.util.List.of(org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SKELETON,
                org.bukkit.entity.EntityType.CREEPER), n, 2, 30, "lightning:" + best.getUniqueId());
        Fx.actionBar(best, "&5El rayo ha despertado algo...");
    }
}
