package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Effects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Lluvia ácida: daño y efectos a jugadores expuestos a la lluvia. Su intensidad escala con el nivel. */
public class AcidRainManager {

    private final ExtremePlugin plugin;
    private final Map<UUID, Long> until = new HashMap<>();      // mundo -> fin
    private final Map<UUID, Long> messageCd = new HashMap<>();  // jugador -> próximo aviso
    private BukkitTask task;

    public AcidRainManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        until.clear();
    }

    public boolean isActive(World w) {
        Long end = until.get(w.getUID());
        return end != null && System.currentTimeMillis() < end;
    }

    public int activeWorlds() { return until.size(); }

    public void start(World w, int seconds) {
        start(w, seconds, false);
    }

    /** @param force true = ignora el interruptor del módulo (comandos de administración) */
    public void start(World w, int seconds, boolean force) {
        YamlConfiguration c = cfg();
        if (!c.getBoolean("acid-rain.enabled", true) || (!force && !plugin.modules().on("nm_acid_rain"))) return;
        int max = (int) (c.getInt("acid-rain.max-duration", 180) * Math.max(1.0, plugin.nightmare().value("event-duration")));
        int sec = Math.max(10, Math.min(seconds, max));
        until.put(w.getUID(), System.currentTimeMillis() + sec * 1000L);
        w.setStorm(true);
        if (w.getWeatherDuration() < sec * 20) w.setWeatherDuration(sec * 20);
        Fx.title(w.getPlayers(), c.getString("acid-rain.title", "&2&lLLUVIA ÁCIDA"), c.getString("acid-rain.subtitle", "&aBúscate un refugio..."));
        if (task == null) {
            long t = Math.max(10L, c.getLong("acid-rain.interval-ticks", 40L));
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, t, t);
        }
    }

    public void stop(World w) {
        until.remove(w.getUID());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            World w = Bukkit.getWorld(en.getKey());
            if (w == null || now >= en.getValue() || !w.hasStorm()) {
                it.remove();
                if (w != null) Fx.chat(w.getPlayers(), "&7La lluvia ácida se detiene...");
                continue;
            }
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                if (exposed(p)) hurt(p);
            }
        }
        if (until.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean exposed(Player p) {
        Location l = p.getEyeLocation();
        return p.getWorld().getHighestBlockYAt(l) <= l.getBlockY() && l.getBlock().getTemperature() > 0.15;
    }

    /** Aplica el daño y los efectos de la lluvia ácida a un jugador (también lo usan las zonas malditas). */
    public void hurt(Player p) {
        YamlConfiguration c = cfg();
        double dmg = c.getDouble("acid-rain.damage", 2.0) * plugin.nightmare().value("acid-intensity");
        if (p.getInventory().getHelmet() != null && c.getBoolean("acid-rain.helmet-reduces", true)) dmg *= 0.6;
        dmg = Math.min(dmg, c.getDouble("acid-rain.max-damage", 8.0));
        p.damage(dmg);
        Fx.particle(p.getEyeLocation(), "LARGE_SMOKE", 4, 0.25, 0.01);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Effects.Spec> effects = Effects.parseSpecs(c.getStringList("acid-rain.effects"));
        for (Effects.Spec s : effects) if (r.nextDouble() < s.chance()) p.addPotionEffect(s.create());

        long now = System.currentTimeMillis();
        Long next = messageCd.get(p.getUniqueId());
        if (next == null || now >= next) {
            p.sendMessage(com.extreme.utils.Text.color(c.getString("acid-rain.message", "&cLa lluvia está quemando tu piel...")));
            messageCd.put(p.getUniqueId(), now + c.getLong("acid-rain.message-cooldown", 30) * 1000L);
            if (messageCd.size() > 200) messageCd.values().removeIf(t -> now > t);
        }
    }
}
