package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Límites y limpieza: global, por jugador, por evento, por multiplicación; presupuesto de explosiones. */
public class PerformanceManager {

    public record Tracked(UUID owner, String event, String key, long since) {}

    private final ExtremePlugin plugin;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private BukkitTask task;

    private int maxGlobal = 250, maxPerPlayer = 50, maxEventMobs = 100, maxBosses = 1, maxExplosionsPerSec = 4;
    private double despawnDistance = 110;
    private long maxAgeMs = 600_000L;
    private long explWindow;
    private int explCount;

    public PerformanceManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("nightmare.yml");
        maxGlobal = Math.max(10, c.getInt("performance.max-mobs-global", 250));
        maxPerPlayer = Math.max(1, c.getInt("performance.max-mobs-per-player", 50));
        maxEventMobs = Math.max(1, c.getInt("performance.max-event-mobs", 100));
        maxBosses = Math.max(0, c.getInt("performance.max-bosses", 1));
        maxExplosionsPerSec = Math.max(1, c.getInt("performance.max-explosions-per-second", 4));
        despawnDistance = c.getDouble("performance.despawn-distance", 110);
        maxAgeMs = Math.max(30, c.getLong("performance.max-age-seconds", 600)) * 1000L;
        if (task != null) task.cancel();
        long ticks = Math.max(100L, c.getLong("performance.cleanup-interval", 30) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, ticks, ticks);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public int maxGlobal() { return maxGlobal; }
    public int maxPerPlayer() { return maxPerPlayer; }
    public int maxEventMobs() { return maxEventMobs; }
    public int maxBosses() { return maxBosses; }

    public void track(Entity e, UUID owner, String event, String key) {
        tracked.put(e.getUniqueId(), new Tracked(owner, event, key, System.currentTimeMillis()));
    }

    public void untrack(UUID id) {
        tracked.remove(id);
    }

    public Tracked info(UUID id) { return tracked.get(id); }

    public int total() { return tracked.size(); }

    public int countOwner(UUID owner) {
        int n = 0;
        for (Tracked t : tracked.values()) if (owner != null && owner.equals(t.owner())) n++;
        return n;
    }

    public int countOwnerKey(UUID owner, String key) {
        int n = 0;
        for (Tracked t : tracked.values()) if (owner != null && owner.equals(t.owner()) && key.equals(t.key())) n++;
        return n;
    }

    public int countEvent(String event) {
        int n = 0;
        for (Tracked t : tracked.values()) if (event != null && event.equals(t.event())) n++;
        return n;
    }

    public int countKey(String key) {
        int n = 0;
        for (Tracked t : tracked.values()) if (key != null && key.equals(t.key())) n++;
        return n;
    }

    /** Presupuesto de explosiones por segundo. Devuelve false si hay que cancelar/omitir la explosión. */
    public boolean tryExplosion() {
        long now = System.currentTimeMillis();
        if (now - explWindow > 1000L) {
            explWindow = now;
            explCount = 0;
        }
        return ++explCount <= maxExplosionsPerSec;
    }

    /** Elimina registros muertos y mobs sobrantes/lejanos o demasiado viejos. */
    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Tracked>> it = tracked.entrySet().iterator();
        int removedFar = 0;
        boolean overLimit = tracked.size() > maxGlobal;
        while (it.hasNext()) {
            Map.Entry<UUID, Tracked> en = it.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (e == null || !e.isValid() || e.isDead()) {
                it.remove();
                continue;
            }
            if (MobTags.getBoss(e) != null) continue;
            boolean old = now - en.getValue().since() > maxAgeMs;
            boolean far = nearestPlayerDistance(e.getLocation()) > despawnDistance;
            if (old || far || (overLimit && removedFar < tracked.size() - maxGlobal)) {
                if (e instanceof LivingEntity) e.remove();
                it.remove();
                removedFar++;
            }
        }
        if (plugin.configs().get("nightmare.yml").getBoolean("debug", false) && removedFar > 0) {
            plugin.getLogger().info("[Pesadilla] Limpieza: " + removedFar + " mobs eliminados. Rastreados: " + tracked.size());
        }
    }

    private double nearestPlayerDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (Player p : new ArrayList<>(loc.getWorld().getPlayers())) {
            best = Math.min(best, p.getLocation().distanceSquared(loc));
        }
        return best == Double.MAX_VALUE ? best : Math.sqrt(best);
    }

    /** Elimina TODOS los mobs rastreados (comando /pesadilla clear). */
    public int clearAll() {
        int n = 0;
        for (UUID id : new ArrayList<>(tracked.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid() && MobTags.getBoss(e) == null) {
                e.remove();
                n++;
            }
        }
        tracked.clear();
        return n;
    }
}
