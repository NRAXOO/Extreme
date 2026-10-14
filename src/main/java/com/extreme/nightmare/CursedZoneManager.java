package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Zonas malditas (esferas definidas en nightmare.yml). Una tarea cada 2 s que solo mira a los jugadores. */
public class CursedZoneManager {

    public record Zone(String name, String world, double x, double y, double z, double radius) {
        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.distanceSquared(new Location(l.getWorld(), x, y, z)) <= radius * radius;
        }
    }

    private final ExtremePlugin plugin;
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, String> inside = new HashMap<>();
    private BukkitTask task;

    public CursedZoneManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void reload() {
        if (task != null) task.cancel();
        zones.clear();
        inside.clear();
        for (Map<?, ?> m : cfg().getMapList("cursed-zones.list")) {
            try {
                zones.add(new Zone(String.valueOf(m.get("name")), String.valueOf(m.get("world")),
                        ((Number) m.get("x")).doubleValue(), ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(), ((Number) m.get("radius")).doubleValue()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Zona maldita inválida en nightmare.yml: " + m);
            }
        }
        if (cfg().getBoolean("cursed-zones.enabled", true) && plugin.modules().on("nm_zones") && plugin.nightmare().isEnabled() && !zones.isEmpty()) {
            long t = Math.max(20L, cfg().getLong("cursed-zones.check-interval", 40L));
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, t, t);
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public List<Zone> zones() { return zones; }

    public void add(Zone z) {
        zones.removeIf(o -> o.name().equalsIgnoreCase(z.name()));
        zones.add(z);
        save();
        reload();
    }

    public boolean remove(String name) {
        boolean r = zones.removeIf(o -> o.name().equalsIgnoreCase(name));
        if (r) {
            save();
            reload();
        }
        return r;
    }

    private void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Zone z : zones) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", z.name());
            m.put("world", z.world());
            m.put("x", z.x());
            m.put("y", z.y());
            m.put("z", z.z());
            m.put("radius", z.radius());
            list.add(m);
        }
        plugin.configs().set("nightmare.yml", "cursed-zones.list", list);
    }

    public Zone at(Location l) {
        for (Zone z : zones) if (z.contains(l)) return z;
        return null;
    }

    public boolean contains(Location l) { return !zones.isEmpty() && plugin.modules().on("nm_zones") && at(l) != null; }

    public boolean inZone(Player p) { return inside.containsKey(p.getUniqueId()); }

    public double spawnMult(Player p) { return inZone(p) ? cfg().getDouble("cursed-zones.spawn-multiplier", 2.5) : 1.0; }
    public double bossMult(Player p) { return inZone(p) ? cfg().getDouble("cursed-zones.boss-multiplier", 3.0) : 1.0; }

    private void tick() {
        YamlConfiguration c = cfg();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Zone z = at(p.getLocation());
            String was = inside.get(p.getUniqueId());
            if (z == null) {
                if (was != null) {
                    inside.remove(p.getUniqueId());
                    // Salida: los efectos se desvanecen progresivamente (duran poco y no se renuevan)
                    p.sendMessage(com.extreme.utils.Text.color(c.getString("cursed-zones.exit-message", "&7Sientes cómo la maldición se aleja...")));
                }
                continue;
            }
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (was == null) {
                inside.put(p.getUniqueId(), z.name());
                Fx.title(p, c.getString("cursed-zones.enter-title", "&5&lZONA MALDITA"), c.getString("cursed-zones.enter-subtitle", "&dAlgo te observa...").replace("{zone}", z.name()));
                Fx.soundTo(plugin, p, "ENTITY_WARDEN_NEARBY_CLOSER", 1f, 0.6f);
            }
            Fx.particle(p.getLocation().add(0, 1, 0), c.getString("cursed-zones.particle", "SCULK_SOUL"), 4, 1.5, 0.02);
            if (r.nextDouble() < 0.15) Fx.soundTo(plugin, p, "AMBIENT_CAVE", 1f, 0.8f);
            if (c.getBoolean("cursed-zones.darkness", true)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false, false));
            }
            if (c.getBoolean("cursed-zones.acid-rain", false) && p.getWorld().hasStorm()) plugin.acidRain().hurt(p);
        }
        inside.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
