package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Administrador de eventos aleatorios. Un sorteo periódico (barato) decide si empieza uno;
 * los eventos activos se procesan en UNA sola tarea que solo existe mientras haya eventos.
 */
public class EventManager {

    public enum Scope { PLAYER, PLAYERS, SERVER }

    public record Def(String key, String name, String subtitle, String chat, boolean enabled, double weight, long cooldownMs,
                      int durationSec, Scope scope, int minLevel, boolean nightOnly, List<EntityType> mobs, int total,
                      int batch, int interval, boolean darkness, boolean storm, boolean acid, boolean lightning,
                      boolean mobRain, boolean blood, String sound) {}

    private static class Active {
        Def def;
        String id;
        World world;
        Set<UUID> players = new HashSet<>();
        long end;
    }

    private final ExtremePlugin plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final List<Active> active = new ArrayList<>();
    private final Map<String, Long> lastStart = new HashMap<>();
    private long globalLast;
    private int counter;
    private BukkitTask checkTask, tickTask;
    private int tickCount;

    public EventManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void reload() {
        shutdown();
        defs.clear();
        YamlConfiguration c = cfg();
        ConfigurationSection root = c.getConfigurationSection("events.list");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                List<EntityType> mobs = new ArrayList<>();
                for (String m : s.getStringList("mobs")) {
                    try {
                        mobs.add(EntityType.valueOf(m.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Mob inválido en evento " + key + ": " + m);
                    }
                }
                Scope scope;
                try {
                    scope = Scope.valueOf(s.getString("scope", "PLAYER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    scope = Scope.PLAYER;
                }
                defs.put(key, new Def(key, s.getString("name", key), s.getString("subtitle", ""),
                        s.getString("chat", "&4&l⚠ {name} &4&l⚠"), s.getBoolean("enabled", true), s.getDouble("weight", 10),
                        (long) (s.getDouble("cooldown", 600) * 1000), s.getInt("duration", 120), scope, s.getInt("min-level", 1),
                        s.getBoolean("night-only", false), mobs, s.getInt("total", 20), s.getInt("batch", 4), s.getInt("interval", 60),
                        s.getBoolean("darkness", false), s.getBoolean("storm", false), s.getBoolean("acid-rain", false),
                        s.getBoolean("lightning", false), s.getBoolean("mob-rain", false), s.getBoolean("blood", false),
                        s.getString("sound", "ENTITY_WITHER_SPAWN")));
            }
        }
        if (c.getBoolean("events.enabled", true) && plugin.modules().on("nm_events") && plugin.nightmare().isEnabled()) {
            long t = Math.max(100L, c.getLong("events.check-interval", 30) * 20L);
            checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::check, t, t);
        }
    }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        if (tickTask != null) tickTask.cancel();
        checkTask = null;
        tickTask = null;
        active.clear();
    }

    public Set<String> keys() { return defs.keySet(); }

    public int activeCount() { return active.size(); }

    // ------------------------------------------------------------------
    //  Consultas usadas por otros sistemas
    // ------------------------------------------------------------------

    public boolean isActiveFor(Player p) {
        for (Active a : active) if (a.players.contains(p.getUniqueId())) return true;
        return false;
    }

    public boolean isBloodActive(World w) {
        for (Active a : active) if (a.def.blood() && a.world.equals(w)) return true;
        return false;
    }

    public boolean isActiveKey(World w, String key) {
        for (Active a : active) if ((a.def.key().equals(key) || a.def.key().equals("apocalypse")) && a.world.equals(w)) return true;
        return false;
    }

    public double eliteMult(World w) {
        return isBloodActive(w) ? cfg().getDouble("events.blood-elite-multiplier", 2.0) : 1.0;
    }

    public double spawnMult(Player p) {
        return isActiveFor(p) ? cfg().getDouble("events.spawn-multiplier", 1.8) : 1.0;
    }

    // ------------------------------------------------------------------
    //  Sorteo
    // ------------------------------------------------------------------

    private void check() {
        YamlConfiguration c = cfg();
        if (!plugin.nightmare().isEnabled() || !plugin.modules().on("nm_events") || !c.getBoolean("events.enabled", true)) return;
        long now = System.currentTimeMillis();
        if (active.size() >= c.getInt("events.max-concurrent", 2)) return;
        if (now - globalLast < c.getLong("events.global-cooldown", 300) * 1000L) return;

        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                    && plugin.nightmare().worldEnabled(p.getWorld()) && eventsFor(p) < c.getInt("events.max-per-player", 1)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player target = candidates.get(r.nextInt(candidates.size()));

        double chance = c.getDouble("events.chance-per-check", 0.25) * plugin.nightmare().value("event-frequency")
                * plugin.vengeance().eventMult(target) * plugin.playerManager().threatMult(target);
        if (r.nextDouble() >= Math.min(chance, 0.9)) return;

        boolean night = isNight(target.getWorld());
        List<Def> pool = new ArrayList<>();
        double total = 0;
        for (Def d : defs.values()) {
            if (!d.enabled() || plugin.nightmare().getLevel() < d.minLevel() || (d.nightOnly() && !night)) continue;
            Long last = lastStart.get(d.key());
            if (last != null && now - last < d.cooldownMs()) continue;
            pool.add(d);
            total += d.weight();
        }
        if (pool.isEmpty() || total <= 0) return;
        double roll = r.nextDouble() * total;
        for (Def d : pool) {
            roll -= d.weight();
            if (roll <= 0) {
                start(d.key(), target, false);
                return;
            }
        }
    }

    private int eventsFor(Player p) {
        int n = 0;
        for (Active a : active) if (a.players.contains(p.getUniqueId())) n++;
        return n;
    }

    private boolean isNight(World w) {
        long t = w.getTime();
        return t >= 13000 && t < 23000;
    }

    // ------------------------------------------------------------------
    //  Inicio / fin
    // ------------------------------------------------------------------

    /** @param forced true = ignora cooldowns y probabilidad (comando de administración) */
    public boolean start(String key, Player target, boolean forced) {
        Def d = defs.get(key);
        if (d == null) return false;
        YamlConfiguration c = cfg();
        if (!forced && (!d.enabled() || active.size() >= c.getInt("events.max-concurrent", 2))) return false;
        if (active.size() >= c.getInt("events.hard-cap", 5)) return false;

        List<Player> players = new ArrayList<>();
        switch (d.scope()) {
            case SERVER -> players.addAll(Bukkit.getOnlinePlayers());
            case PLAYERS -> {
                for (Player p : target.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(target.getLocation()) < 128 * 128) players.add(p);
                }
            }
            default -> players.add(target);
        }
        players.removeIf(p -> p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE
                || (!forced && eventsFor(p) >= c.getInt("events.max-per-player", 1)));
        if (players.isEmpty()) return false;

        Active a = new Active();
        a.def = d;
        a.world = target.getWorld();
        a.id = d.key() + "#" + (++counter);
        for (Player p : players) a.players.add(p.getUniqueId());
        double dur = d.durationSec() * plugin.nightmare().value("event-duration");
        a.end = System.currentTimeMillis() + (long) (dur * 1000);
        active.add(a);
        lastStart.put(d.key(), System.currentTimeMillis());
        globalLast = System.currentTimeMillis();

        // Anuncio
        String name = d.name();
        Fx.title(players, name, d.subtitle());
        Fx.chat(players, d.chat().replace("{name}", name));
        for (Player p : players) Fx.soundTo(plugin, p, d.sound(), 0.9f, 0.7f);

        // Hordas progresivas
        if (!d.mobs().isEmpty()) {
            for (Player p : players) {
                int total = (int) Math.round(d.total() * plugin.nightmare().value("mob-count"));
                plugin.hordes().start(p, d.mobs(), Math.min(total, plugin.perf().maxEventMobs()), d.batch(), d.interval(), a.id);
            }
        }
        if (d.storm()) {
            a.world.setStorm(true);
            a.world.setThundering(true);
            a.world.setWeatherDuration((int) (dur * 20));
            a.world.setThunderDuration((int) (dur * 20));
        }
        if (d.acid()) plugin.acidRain().start(a.world, (int) dur, forced);

        if (tickTask == null) tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    private void tick() {
        tickCount++;
        long now = System.currentTimeMillis();
        YamlConfiguration c = cfg();
        Iterator<Active> it = active.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (now >= a.end) {
                it.remove();
                finish(a);
                continue;
            }
            for (UUID id : a.players) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline() || p.isDead()) continue;
                perPlayer(a, p, c);
            }
        }
        if (active.isEmpty() && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void perPlayer(Active a, Player p, YamlConfiguration c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Def d = a.def;
        if (d.darkness() && tickCount % 5 == 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, true, false, false));
        }
        if (d.blood()) {
            Fx.particle(p.getLocation().add(0, 1, 0), "DAMAGE_INDICATOR", 6, 1.5, 0.0);
            if (tickCount % 8 == 0) Fx.soundTo(plugin, p, "ENTITY_WARDEN_HEARTBEAT", 1f, 0.8f);
        }
        if (d.lightning() && r.nextDouble() < c.getDouble("events.lightning-chance", 0.12)) {
            Location l = p.getLocation().add(r.nextDouble(-18, 18), 0, r.nextDouble(-18, 18));
            l.setY(a.world.getHighestBlockYAt(l) + 1);
            if (l.distanceSquared(p.getLocation()) > 36) a.world.strikeLightning(l);
        }
        if (d.mobRain() && tickCount % 2 == 0 && !d.mobs().isEmpty()) {
            if (plugin.perf().countEvent(a.id) < plugin.perf().maxEventMobs()
                    && plugin.perf().countOwner(p.getUniqueId()) < plugin.perf().maxPerPlayer()) {
                Location l = p.getLocation().add(r.nextDouble(-9, 9), c.getDouble("events.mob-rain-height", 16), r.nextDouble(-9, 9));
                EntityType t = d.mobs().get(r.nextInt(d.mobs().size()));
                var le = plugin.spawns().spawnAt(t, l, p.getUniqueId(), a.id);
                if (le instanceof org.bukkit.entity.Mob m) m.setTarget(p);
            }
        }
    }

    private void finish(Active a) {
        List<Player> players = new ArrayList<>();
        for (UUID id : a.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) players.add(p);
        }
        Fx.chat(players, cfg().getString("events.end-message", "&7El evento ha terminado... por ahora.").replace("{name}", a.def.name()));
    }

    /** Termina todos los eventos activos (comando de administración). */
    public void stopAll() {
        long now = System.currentTimeMillis();
        for (Active a : active) a.end = now;
    }
}
