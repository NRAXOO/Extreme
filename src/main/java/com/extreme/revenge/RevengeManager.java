package com.extreme.revenge;

import com.extreme.ExtremePlugin;
import com.extreme.lives.PlayerData;
import com.extreme.nightmare.Fx;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Venganza entre jugadores: cuando alguien mata a otro, la víctima tiene un tiempo limitado para vengarse.
 * Si lo logra, le roba un corazón (vida) al asesino. Los datos se guardan en revenges.yml.
 */
public class RevengeManager {

    public record Revenge(UUID victim, UUID killer, String killerName, long expires) {}

    private final ExtremePlugin plugin;
    private final File file;
    /** víctima -> venganza pendiente contra su asesino */
    private final Map<UUID, Revenge> byVictim = new HashMap<>();
    private BukkitTask task;

    public RevengeManager(ExtremePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "revenges.yml");
    }

    private YamlConfiguration cfg() { return plugin.configs().get("revenge.yml"); }

    public boolean isEnabled() { return cfg().getBoolean("revenge.enabled", true); }

    public void load() {
        byVictim.clear();
        YamlConfiguration y = plugin.io().load(file);
        ConfigurationSection s = y.getConfigurationSection("revenges");
        if (s == null) return;
        long now = System.currentTimeMillis();
        for (String key : s.getKeys(false)) {
            try {
                ConfigurationSection e = s.getConfigurationSection(key);
                if (e == null) continue;
                long exp = e.getLong("expires");
                if (exp <= now) continue;
                UUID victim = UUID.fromString(key);
                byVictim.put(victim, new Revenge(victim, UUID.fromString(e.getString("killer", "")), e.getString("killer-name", "?"), exp));
            } catch (IllegalArgumentException ignored) {
                // entrada corrupta
            }
        }
        ensureTask();
    }

    public void reload() { ensureTask(); }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Revenge r : byVictim.values()) {
            String b = "revenges." + r.victim();
            y.set(b + ".killer", r.killer().toString());
            y.set(b + ".killer-name", r.killerName());
            y.set(b + ".expires", r.expires());
        }
        plugin.io().saveAsync(file, y.saveToString());
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    public Revenge get(UUID victim) {
        Revenge r = byVictim.get(victim);
        if (r != null && r.expires() <= System.currentTimeMillis()) {
            byVictim.remove(victim);
            save();
            return null;
        }
        return r;
    }

    public long remainingMs(UUID victim) {
        Revenge r = get(victim);
        return r == null ? 0L : Math.max(0L, r.expires() - System.currentTimeMillis());
    }

    /** ¿Alguien tiene una venganza pendiente CONTRA este jugador? */
    public int hunters(UUID killer) {
        int n = 0;
        long now = System.currentTimeMillis();
        for (Revenge r : byVictim.values()) if (r.killer().equals(killer) && r.expires() > now) n++;
        return n;
    }

    // ------------------------------------------------------------------
    //  Muertes entre jugadores
    // ------------------------------------------------------------------

    public void onPlayerKill(Player victim, Player killer) {
        if (!isEnabled() || victim.equals(killer)) return;
        YamlConfiguration c = cfg();

        // 1) ¿La víctima de ahora tenía una venganza pendiente contra este asesino? (el "asesino" de ahora es quien se venga)
        Revenge pending = get(killer.getUniqueId());
        if (pending != null && pending.killer().equals(victim.getUniqueId())) {
            fulfill(pending, killer, victim);
            if (!c.getBoolean("revenge.revenge-kill-creates-revenge", false)) return;
        }

        // 2) Se abre una nueva venganza para la víctima
        long duration = TimeUtil.parse(c.getString("revenge.duration"), 600_000L);
        Revenge r = new Revenge(victim.getUniqueId(), killer.getUniqueId(), killer.getName(), System.currentTimeMillis() + duration);
        byVictim.put(victim.getUniqueId(), r);
        save();
        ensureTask();

        String time = TimeUtil.format(duration);
        plugin.messages().send(victim, "revenge-started", "killer", killer.getName(), "time", time, "lives", steal());
        Fx.title(victim, plugin.messages().str("revenge-title"), plugin.messages().str("revenge-subtitle", "killer", killer.getName(), "time", time));
        plugin.sounds().playTo(victim, "revenge-start");
        if (c.getBoolean("revenge.warn-killer", true)) {
            plugin.messages().send(killer, "revenge-warning", "victim", victim.getName(), "time", time);
        }
        if (c.getBoolean("revenge.broadcast", false)) {
            plugin.messages().broadcast("revenge-broadcast", "victim", victim.getName(), "killer", killer.getName());
        }
        if (c.getBoolean("revenge.glow-killer", false)) {
            int ticks = (int) Math.min(Integer.MAX_VALUE / 2, duration / 50);
            killer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, true, false, false));
        }
    }

    private int steal() {
        return Math.max(1, cfg().getInt("revenge.steal-lives", 1));
    }

    private void fulfill(Revenge r, Player avenger, Player target) {
        YamlConfiguration c = cfg();
        byVictim.remove(r.victim());
        save();

        int amount = steal();
        PlayerData td = plugin.lives().getOrCreate(target);
        PlayerData ad = plugin.lives().getOrCreate(avenger);
        boolean livesOn = plugin.lives().isEnabled();
        int stolen = amount;
        if (livesOn) {
            // El asesino no puede quedar en 0 por una venganza, salvo que se permita
            if (!c.getBoolean("revenge.can-eliminate-target", false)) stolen = Math.max(0, Math.min(amount, td.lives - 1));
            else stolen = Math.min(amount, td.lives);
            int room = Math.max(0, plugin.lives().getMaxLives() - ad.lives);
            int gained = c.getBoolean("revenge.respect-maximum", true) ? Math.min(stolen, room) : stolen;
            if (stolen > 0) {
                plugin.lives().addLives(td, -stolen);
                if (gained > 0) plugin.lives().addLives(ad, gained);
            }
            stolen = Math.max(stolen, 0);
            plugin.lives().saveIfDirty();
        }
        plugin.messages().send(avenger, "revenge-fulfilled-avenger", "target", target.getName(), "lives", stolen,
                "total", ad.lives, "max_lives", plugin.lives().getMaxLives());
        plugin.messages().send(target, "revenge-fulfilled-target", "avenger", avenger.getName(), "lives", stolen,
                "total", td.lives);
        Fx.title(avenger, plugin.messages().str("revenge-done-title"), plugin.messages().str("revenge-done-subtitle", "target", target.getName()));
        plugin.sounds().playTo(avenger, "revenge-done");
        plugin.sounds().playTo(target, "life-lost");
        if (c.getBoolean("revenge.broadcast", false)) {
            plugin.messages().broadcast("revenge-done-broadcast", "avenger", avenger.getName(), "target", target.getName());
        }
        // El brillo ya no hace falta
        target.removePotionEffect(PotionEffectType.GLOWING);
    }

    // ------------------------------------------------------------------
    //  Expiración (una sola tarea, solo mientras haya venganzas)
    // ------------------------------------------------------------------

    private void ensureTask() {
        if (byVictim.isEmpty()) {
            if (task != null) task.cancel();
            task = null;
            return;
        }
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 100L, 100L);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Revenge> it = byVictim.values().iterator();
        while (it.hasNext()) {
            Revenge r = it.next();
            if (r.expires() > now) continue;
            it.remove();
            changed = true;
            Player v = Bukkit.getPlayer(r.victim());
            if (v != null) {
                plugin.messages().send(v, "revenge-expired", "killer", r.killerName());
                v.sendActionBar(com.extreme.utils.Text.color(plugin.messages().str("revenge-expired-bar")));
            }
        }
        if (changed) save();
        ensureTask();
    }

    public boolean cancel(UUID victim) {
        boolean r = byVictim.remove(victim) != null;
        if (r) save();
        return r;
    }

    public int activeCount() { return byVictim.size(); }

    public Iterable<Revenge> all() { return byVictim.values(); }
}
