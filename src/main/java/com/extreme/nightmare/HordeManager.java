package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Hordas progresivas: los mobs aparecen por oleadas alrededor del jugador y todos lo atacan. */
public class HordeManager {

    private static class Horde {
        UUID player;
        List<EntityType> types;
        int remaining, batch, interval, cooldown;
        String id;
    }

    private final ExtremePlugin plugin;
    private final List<Horde> hordes = new ArrayList<>();
    private BukkitTask task;

    public HordeManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        hordes.clear();
    }

    public int activeCount() { return hordes.size(); }

    public boolean hasHorde(UUID player) {
        for (Horde h : hordes) if (h.player.equals(player)) return true;
        return false;
    }

    /**
     * @param total  mobs totales a generar (ya escalado por nivel por quien llama)
     * @param batch  mobs por oleada
     * @param interval ticks entre oleadas
     */
    public boolean start(Player p, List<EntityType> types, int total, int batch, int interval, String id) {
        int maxPer = plugin.configs().get("nightmare.yml").getInt("hordes.max-per-player", 1);
        int count = 0;
        for (Horde h : hordes) if (h.player.equals(p.getUniqueId())) count++;
        if (count >= maxPer) return false;
        if (types.isEmpty() || total <= 0) return false;

        Horde h = new Horde();
        h.player = p.getUniqueId();
        h.types = new ArrayList<>(types);
        h.remaining = total;
        h.batch = Math.max(1, batch);
        h.interval = Math.max(10, interval);
        h.cooldown = 0;
        h.id = id;
        hordes.add(h);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        return true;
    }

    private void tick() {
        Iterator<Horde> it = hordes.iterator();
        while (it.hasNext()) {
            Horde h = it.next();
            Player p = Bukkit.getPlayer(h.player);
            if (p == null || !p.isOnline() || p.isDead()) {
                it.remove();
                continue;
            }
            h.cooldown -= 10;
            if (h.cooldown > 0) continue;
            h.cooldown = h.interval;

            double min = plugin.configs().get("nightmare.yml").getDouble("hordes.min-distance", 10);
            double max = plugin.configs().get("nightmare.yml").getDouble("hordes.max-distance", 22);
            int n = Math.min(h.batch, h.remaining);
            for (int i = 0; i < n; i++) {
                EntityType t = h.types.get(ThreadLocalRandom.current().nextInt(h.types.size()));
                List<LivingEntity> spawned = plugin.spawns().spawnAround(p, t, 1, h.id, min, max);
                for (LivingEntity le : spawned) {
                    if (le instanceof Mob m) {
                        m.setTarget(p);
                        plugin.ai().track(m, true);
                    }
                }
                h.remaining--;
            }
            // Si el límite de evento impide generar, se descarta lo que falta
            if (h.remaining <= 0 || (h.id != null && plugin.perf().countEvent(h.id) >= plugin.perf().maxEventMobs())) it.remove();
        }
        if (hordes.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }
}
