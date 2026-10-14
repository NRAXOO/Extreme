package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multiplicación genérica configurable por mob: probabilidad, cantidad, cooldown, máximo de entidades,
 * generaciones máximas y condición de activación (DAMAGE = al recibir daño, HIT = al golpear al jugador).
 */
public class MultiplicationManager {

    private record Rule(double chance, int min, int max, long cooldownMs, int maxEntities, int maxGen, String trigger, int minLevel) {}

    private final ExtremePlugin plugin;
    private final Map<String, Rule> rules = new HashMap<>();
    private boolean enabled;

    public MultiplicationManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        rules.clear();
        YamlConfiguration c = plugin.configs().get("nightmare.yml");
        enabled = c.getBoolean("multiplication.enabled", true);
        ConfigurationSection root = c.getConfigurationSection("multiplication");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            rules.put(key.toLowerCase(Locale.ROOT), new Rule(
                    s.getDouble("chance", 0.1), Math.max(1, s.getInt("amount-min", 1)), Math.max(1, s.getInt("amount-max", 2)),
                    (long) (s.getDouble("cooldown", 8) * 1000), s.getInt("max-entities", 30), s.getInt("max-generations", 3),
                    s.getString("trigger", "DAMAGE").toUpperCase(Locale.ROOT), s.getInt("min-level", 1)));
        }
    }

    public String keyOf(LivingEntity e) {
        String t = MobTags.getType(e);
        if ("mini_zombie".equals(t)) return "mini_zombie";
        return e.getType().name().toLowerCase(Locale.ROOT);
    }

    /** @param cause jugador que provocó la multiplicación (puede ser null) */
    public void tryMultiply(LivingEntity src, Player cause, String trigger) {
        if (!enabled || !plugin.modules().on("nm_multiplication") || !plugin.nightmare().worldEnabled(src.getWorld())) return;
        String key = keyOf(src);
        Rule r = rules.get(key);
        if (r == null || !r.trigger().equals(trigger) || plugin.nightmare().getLevel() < r.minLevel()) return;

        int gen = MobTags.getGen(src);
        if (gen >= r.maxGen()) return;
        long now = System.currentTimeMillis();
        if (now < MobTags.getLong(src, MobTags.NEXT_MULT)) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= r.chance() * plugin.nightmare().value("multiplication")) return;
        if (plugin.perf().countKey(key) >= r.maxEntities()) return;

        PerformanceManager.Tracked info = plugin.perf().info(src.getUniqueId());
        UUID owner = cause != null ? cause.getUniqueId() : (info != null ? info.owner() : null);
        if (owner != null && plugin.perf().countOwner(owner) >= plugin.perf().maxPerPlayer()) return;

        MobTags.setLong(src, MobTags.NEXT_MULT, now + r.cooldownMs());
        int amount = r.min() >= r.max() ? r.min() : rnd.nextInt(r.min(), r.max() + 1);
        String type = MobTags.getType(src);
        String event = MobTags.getEvent(src);
        EntityType childType = key.equals("mini_zombie") ? EntityType.ZOMBIE : src.getType();
        Location base = src.getLocation();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < amount; i++) {
                Location spot = plugin.spawns().spotNear(base, 4);
                LivingEntity child = plugin.spawns().spawnAt(childType, spot, owner, event);
                if (child == null) break;
                MobTags.setGen(child, gen + 1);
                if ("mini_zombie".equals(type) && child instanceof Zombie z) {
                    z.setBaby(true);
                    MobTags.setType(z, "mini_zombie");
                } else if (type != null) {
                    MobTags.setType(child, type);
                }
                if (child instanceof Mob m && cause != null) m.setTarget(cause);
                Fx.particle(spot.clone().add(0, 1, 0), "SMOKE", 12, 0.3, 0.02);
            }
            Fx.sound(plugin, base, "ENTITY_ZOMBIE_VILLAGER_CONVERTED", 0.5f, 1.4f);
        });
    }
}
