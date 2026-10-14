package com.extreme.mobs;

import com.extreme.ExtremePlugin;
import com.extreme.difficulty.DifficultyProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cobwebs de araña. Anti-spam: probabilidad, cooldown por araña, máximo de cobwebs por uso,
 * límite global de cobwebs activas y desaparición automática. Solo reemplaza bloques de aire.
 */
public class SpiderManager {

    private final ExtremePlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Set<Block> active = new HashSet<>();

    private boolean enabled;
    private double baseChance;
    private long cooldownMs;
    private int radius, maxPerUse, durationTicks, maxActive, minimumDay;
    private Set<EntityType> types = EnumSet.noneOf(EntityType.class);
    private Set<String> worlds = new HashSet<>();

    public SpiderManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("mobs.yml");
        String p = "spider.cobweb.";
        enabled = c.getBoolean(p + "enabled", true);
        baseChance = c.getDouble(p + "chance", 15);
        cooldownMs = Math.max(0L, (long) (c.getDouble(p + "cooldown", 5) * 1000));
        radius = Math.max(0, Math.min(6, c.getInt(p + "radius", 2)));
        maxPerUse = Math.max(1, c.getInt(p + "max-webs-per-use", 6));
        durationTicks = Math.max(20, (int) (c.getDouble(p + "duration", 8) * 20));
        maxActive = Math.max(1, c.getInt(p + "max-active-webs", 150));
        minimumDay = c.getInt(p + "minimum-day", 1);
        worlds = new HashSet<>(c.getStringList(p + "worlds"));
        Set<EntityType> t = EnumSet.noneOf(EntityType.class);
        for (String s : c.getStringList(p + "types")) {
            try {
                t.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Tipo inválido en spider.cobweb.types: " + s);
            }
        }
        types = t;
    }

    public boolean isCobwebSpider(Entity e) {
        return enabled && plugin.modules().on("spider_cobweb") && types.contains(e.getType());
    }

    private double currentChance() {
        DifficultyProfile prof = plugin.difficulty().getProfile();
        return (plugin.difficulty().isEnabled() && prof.cobwebChance() >= 0) ? prof.cobwebChance() : baseChance;
    }

    /** Intenta generar cobwebs alrededor de un jugador durante un combate con una araña. */
    public void trigger(Entity spider, Player target) {
        if (!isCobwebSpider(spider)) return;
        World w = target.getWorld();
        if (!worlds.isEmpty() && !worlds.contains(w.getName())) return;
        if (plugin.difficulty().effectiveDay() < minimumDay) return;
        if (active.size() >= maxActive) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(spider.getUniqueId());
        if (last != null && now - last < cooldownMs) return;

        double chance = currentChance();
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() * 100.0 >= chance) return;

        lastUse.put(spider.getUniqueId(), now);
        if (lastUse.size() > 512) lastUse.values().removeIf(t -> now - t > cooldownMs * 2 + 1000);
        place(target.getLocation().getBlock());
    }

    public void forget(UUID spiderId) {
        lastUse.remove(spiderId);
    }

    /** Genera cobwebs alrededor de un bloque (respeta límites y desaparición automática). */
    public void place(Block feet) {
        World w = feet.getWorld();
        List<Block> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    Block b = w.getBlockAt(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (b.getType().isAir() && !b.equals(feet)) candidates.add(b);
                }
            }
        }
        Collections.shuffle(candidates);

        List<Block> placed = new ArrayList<>();
        if (feet.getType().isAir()) placed.add(feet); // siempre intenta atrapar al jugador
        for (Block b : candidates) {
            if (placed.size() >= maxPerUse) break;
            placed.add(b);
        }
        if (placed.isEmpty()) return;

        for (Block b : placed) {
            b.setType(Material.COBWEB, false);
            active.add(b);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(placed), durationTicks);
    }

    private void remove(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
            active.remove(b);
        }
    }

    /** Quita todas las cobwebs creadas (al apagar el plugin). */
    public void cleanup() {
        for (Block b : new ArrayList<>(active)) {
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
        }
        active.clear();
        lastUse.clear();
    }
}
