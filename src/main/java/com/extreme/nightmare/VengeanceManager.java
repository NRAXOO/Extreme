package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Venganza del mundo: si un jugador mata demasiados mobs rápido (o tras morir) el mundo se enfurece:
 * más spawn, mobs buffeados, élites, más eventos y más probabilidad de mini-jefes. Sin tareas propias.
 */
public class VengeanceManager {

    private final ExtremePlugin plugin;
    private final Map<UUID, ArrayDeque<Long>> kills = new HashMap<>();
    private final Map<UUID, Long> until = new HashMap<>();

    public VengeanceManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public boolean isActive(Player p) {
        if (!plugin.modules().on("nm_fury")) return false;
        Long end = until.get(p.getUniqueId());
        if (end == null) return false;
        if (System.currentTimeMillis() > end) {
            until.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    public double spawnMult(Player p) { return isActive(p) ? cfg().getDouble("vengeance.spawn-multiplier", 2.0) : 1.0; }
    public double bossMult(Player p) { return isActive(p) ? cfg().getDouble("vengeance.boss-multiplier", 3.0) : 1.0; }
    public double eventMult(Player p) { return isActive(p) ? cfg().getDouble("vengeance.event-multiplier", 2.0) : 1.0; }

    public void onKill(Player killer, LivingEntity mob) {
        YamlConfiguration c = cfg();
        if (!plugin.modules().on("nm_fury") || !c.getBoolean("vengeance.enabled", true) || !(mob instanceof Enemy) || isActive(killer)) return;
        if (!plugin.nightmare().worldEnabled(killer.getWorld())) return;
        long now = System.currentTimeMillis();
        long window = c.getLong("vengeance.window-seconds", 20) * 1000L;
        ArrayDeque<Long> q = kills.computeIfAbsent(killer.getUniqueId(), k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
        if (q.size() >= c.getInt("vengeance.kills-threshold", 12)) {
            q.clear();
            activate(killer, c.getLong("vengeance.duration-seconds", 180) * 1000L, true);
        }
    }

    public void onPlayerDeath(Player p) {
        YamlConfiguration c = cfg();
        if (!plugin.modules().on("nm_fury") || !c.getBoolean("vengeance.on-death.enabled", true) || !plugin.nightmare().worldEnabled(p.getWorld())) return;
        activate(p, c.getLong("vengeance.on-death.duration-seconds", 300) * 1000L, false);
    }

    private void activate(Player p, long ms, boolean spree) {
        YamlConfiguration c = cfg();
        until.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        Fx.title(p, spree ? "&4&lLA PESADILLA SE ENFURECE" : "&4&lFURIA DEL MUNDO", spree ? "&cHas matado demasiado... ellos lo saben." : "&cEl mundo huele tu debilidad.");
        Fx.soundTo(plugin, p, "ENTITY_WITHER_AMBIENT", 1f, 0.6f);

        if (!spree) return; // tras morir solo se activa el modo (el jugador reaparece sin enemigos encima)
        // Buff a los mobs cercanos + élite + posible invasión
        for (var ent : p.getNearbyEntities(32, 16, 32)) {
            if (ent instanceof Enemy && ent instanceof LivingEntity le && !(le instanceof Player)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 1));
                le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 30, 1));
                if (le instanceof Mob m) m.setTarget(p);
            }
        }
        Location loc = plugin.spawns().findLocation(p, 12, 22);
        if (loc != null) {
            LivingEntity elite = plugin.spawns().spawnAt(EntityType.ZOMBIE, loc, p.getUniqueId(), null);
            if (elite != null) {
                plugin.nmMobs().makeElite(elite);
                if (elite instanceof Mob m) {
                    m.setTarget(p);
                    plugin.ai().track(m, true);
                }
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < c.getDouble("vengeance.horde-chance", 0.4)) {
            int n = (int) Math.round(12 * plugin.nightmare().value("mob-count"));
            plugin.hordes().start(p, List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER), Math.min(n, 50), 4, 40, "vengeance:" + p.getUniqueId());
        }
    }

    public int activeCount() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : until.values()) if (t > now) n++;
        return n;
    }
}
