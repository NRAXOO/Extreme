package com.extreme.mobs;

import com.extreme.ExtremePlugin;
import com.extreme.difficulty.DifficultyProfile;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Multiplicadores de daño y vida por mob (mobs.yml) combinados con la dificultad del día. */
public class MobManager {

    private record Mult(double damage, double health) {}

    private final ExtremePlugin plugin;
    private final NamespacedKey keyHealth;
    private final NamespacedKey keySpeed;
    private final NamespacedKey keyRange;

    private boolean enabled = true;
    private double globalDamage = 1.0, globalHealth = 1.0, maxDamage = 10.0, maxHealth = 10.0;
    private Set<String> worlds = new HashSet<>();
    private Set<EntityType> excluded = new HashSet<>();
    private Set<CreatureSpawnEvent.SpawnReason> ignoredReasons = new HashSet<>();
    private final Map<EntityType, Mult> perType = new EnumMap<>(EntityType.class);

    public MobManager(ExtremePlugin plugin) {
        this.plugin = plugin;
        this.keyHealth = new NamespacedKey(plugin, "mob_health");
        this.keySpeed = new NamespacedKey(plugin, "mob_speed");
        this.keyRange = new NamespacedKey(plugin, "mob_range");
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("mobs.yml");
        enabled = c.getBoolean("mobs.enabled", true);
        globalDamage = c.getDouble("mobs.global-damage-multiplier", 1.0);
        globalHealth = c.getDouble("mobs.global-health-multiplier", 1.0);
        maxDamage = Math.max(1.0, c.getDouble("mobs.limits.max-damage-multiplier", 10.0));
        maxHealth = Math.max(1.0, c.getDouble("mobs.limits.max-health-multiplier", 10.0));
        worlds = new HashSet<>(c.getStringList("mobs.worlds"));

        excluded = new HashSet<>();
        for (String s : c.getStringList("mobs.exclude")) {
            EntityType t = type(s);
            if (t != null) excluded.add(t);
        }
        ignoredReasons = new HashSet<>();
        for (String s : c.getStringList("mobs.ignore-spawn-reasons")) {
            try {
                ignoredReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("SpawnReason desconocido en mobs.yml: " + s);
            }
        }

        perType.clear();
        ConfigurationSection root = c.getConfigurationSection("mobs");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null || key.equals("limits")) continue;
            EntityType t = type(key);
            if (t == null) {
                plugin.getLogger().warning("Tipo de mob desconocido en mobs.yml: " + key);
                continue;
            }
            perType.put(t, new Mult(s.getDouble("damage-multiplier", 1.0), s.getDouble("health-multiplier", 1.0)));
        }
    }

    private EntityType type(String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean worldAllowed(World w) {
        return worlds.isEmpty() || worlds.contains(w.getName());
    }

    /** ¿Este mob está gestionado por el plugin? (hostiles o con sección propia en mobs.yml) */
    public boolean handles(LivingEntity e) {
        if (!enabled || !active() || excluded.contains(e.getType())) return false;
        return e instanceof Enemy || (plugin.modules().on("mob_stats") && perType.containsKey(e.getType()));
    }

    /** Hay algún sistema que modifica mobs: multiplicadores base, dificultad manual o Pesadilla. */
    private boolean active() {
        return plugin.modules().on("mob_stats") || plugin.difficulty().isEnabled() || plugin.nightmare().isEnabled();
    }

    public double damageMultiplier(EntityType t) {
        Mult m = perType.get(t);
        double base = plugin.modules().on("mob_stats") ? globalDamage * (m == null ? 1.0 : m.damage()) : 1.0;
        double v = base * plugin.difficulty().getProfile().damage()
                * plugin.nightmare().mult("damage");
        return Math.min(v, maxDamage);
    }

    public double healthMultiplier(EntityType t) {
        Mult m = perType.get(t);
        double base = plugin.modules().on("mob_stats") ? globalHealth * (m == null ? 1.0 : m.health()) : 1.0;
        double v = base * plugin.difficulty().getProfile().health()
                * plugin.nightmare().mult("health");
        return Math.min(v, maxHealth);
    }

    /** Aplica vida, velocidad, rango y buffs a un mob recién aparecido. */
    public void applySpawn(LivingEntity e, CreatureSpawnEvent.SpawnReason reason) {
        if (!handles(e) || ignoredReasons.contains(reason) || !worldAllowed(e.getWorld())) return;
        DifficultyProfile profile = plugin.difficulty().getProfile();

        double hm = healthMultiplier(e.getType());
        boolean healthChanged = setModifier(e, Attribute.MAX_HEALTH, keyHealth, hm - 1.0);
        setModifier(e, Attribute.MOVEMENT_SPEED, keySpeed, profile.speed() * plugin.nightmare().mult("speed") - 1.0);
        double rangeMult = profile.followRange();
        AttributeInstance fr = e.getAttribute(Attribute.FOLLOW_RANGE);
        if (fr != null && plugin.nightmare().isEnabled() && plugin.modules().on("nm_detection") && fr.getBaseValue() > 0) {
            rangeMult = Math.max(rangeMult, plugin.nightmare().detection() / fr.getBaseValue());
        }
        setModifier(e, Attribute.FOLLOW_RANGE, keyRange, rangeMult - 1.0);

        if (healthChanged) {
            AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) e.setHealth(hp.getValue());
        }

        if (profile.buffChance() > 0 && e instanceof Enemy
                && ThreadLocalRandom.current().nextDouble() * 100.0 < profile.buffChance()) {
            applyBuffs(e, profile.buffAmplifier());
        }
    }

    private void applyBuffs(LivingEntity e, int amplifier) {
        List<PotionEffectType> pool = new ArrayList<>(plugin.difficulty().getBuffTypes());
        if (pool.isEmpty()) return;
        Collections.shuffle(pool);
        int n = Math.min(plugin.difficulty().getBuffCount(), pool.size());
        for (int i = 0; i < n; i++) {
            e.addPotionEffect(new PotionEffect(pool.get(i), PotionEffect.INFINITE_DURATION, amplifier, true, false));
        }
    }

    /** Sustituye (nunca acumula) el modificador propio del plugin. Devuelve true si hay modificador activo. */
    public boolean setModifier(LivingEntity e, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return false;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (key.equals(m.getKey())) inst.removeModifier(m);
        }
        if (Math.abs(amount) < 1.0E-6) return false;
        inst.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
        return true;
    }
}
