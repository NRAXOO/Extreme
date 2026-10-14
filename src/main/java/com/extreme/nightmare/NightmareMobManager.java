package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modificaciones al aparecer cada mob: buffs generales (Fuerza/Velocidad), élites, variantes de zombies,
 * creepers, esqueletos, golems, animales hostiles y pollos explosivos. Todo identificado con PDC.
 */
public class NightmareMobManager {

    private final ExtremePlugin plugin;
    private final NamespacedKey kHealth, kDamage, kSpeed, kScale;

    public NightmareMobManager(ExtremePlugin plugin) {
        this.plugin = plugin;
        kHealth = new NamespacedKey(plugin, "nm_health");
        kDamage = new NamespacedKey(plugin, "nm_damage");
        kSpeed = new NamespacedKey(plugin, "nm_speed");
        kScale = new NamespacedKey(plugin, "nm_scale");
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    public void apply(LivingEntity le, CreatureSpawnEvent.SpawnReason reason) {
        if (!plugin.nightmare().worldEnabled(le.getWorld())) return;
        if (MobTags.getBoss(le) != null) return; // los jefes los configura BossManager
        EntityType t = le.getType();
        if (t == EntityType.ENDER_DRAGON || t == EntityType.WITHER || t == EntityType.PLAYER) return;

        if (le instanceof Enemy) hostile(le, reason);
        else if (le instanceof IronGolem && plugin.modules().on("nm_golems")) golem(le);
        else if (le instanceof Animals a) animal(a, reason);
    }

    // ------------------------------------------------------------------
    //  Hostiles
    // ------------------------------------------------------------------

    private void hostile(LivingEntity le, CreatureSpawnEvent.SpawnReason reason) {
        YamlConfiguration c = cfg();
        int level = plugin.nightmare().getLevel();

        // Buff general: Fuerza II y Velocidad II (suben a III en niveles altos)
        int strength = c.getInt("mobs.strength", 2) + (level >= c.getInt("mobs.strength-upgrade-level", 10) ? 1 : 0);
        int speed = c.getInt("mobs.speed", 2) + (level >= c.getInt("mobs.speed-upgrade-level", 10) ? 1 : 0);
        if (plugin.modules().on("nm_buffs")) {
            buff(le, PotionEffectType.STRENGTH, strength);
            buff(le, PotionEffectType.SPEED, speed);
            if (level >= c.getInt("mobs.resistance-from-level", 6)) buff(le, PotionEffectType.RESISTANCE, c.getInt("mobs.resistance", 1));
            if (level >= c.getInt("mobs.regeneration-from-level", 12)) buff(le, PotionEffectType.REGENERATION, c.getInt("mobs.regeneration", 1));
        }
        if (plugin.events().isBloodActive(le.getWorld())) buff(le, PotionEffectType.STRENGTH, strength + 1);
        if (plugin.zones().contains(le.getLocation())) {
            buff(le, PotionEffectType.STRENGTH, strength + 1);
            buff(le, PotionEffectType.RESISTANCE, 1);
        }

        boolean track = false;

        switch (le.getType()) {
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> {
                if (plugin.modules().on("nm_zombies")) track |= zombie((Zombie) le);
            }
            case SPIDER, CAVE_SPIDER -> {
                if (plugin.modules().on("nm_spiders")) track |= spider(le);
            }
            case CREEPER -> {
                if (plugin.modules().on("nm_creepers")) creeper((Creeper) le);
            }
            case GHAST -> {
                if (plugin.modules().on("nm_ghast")) track |= ghast(le);
            }
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> {
                if (plugin.modules().on("nm_piglins") && piglinTypes().contains(le.getType().name())) {
                    MobTags.setType(le, "piglin_angry");
                    track = true;
                }
            }
            case ENDERMAN -> {
                if (plugin.modules().on("nm_endermen")) {
                    MobTags.setType(le, "enderman_hunter");
                    track = true;
                }
            }
            default -> {
                if (le instanceof AbstractSkeleton sk && plugin.modules().on("nm_skeletons")) track |= skeleton(sk);
            }
        }

        // Élite
        double eliteChance = plugin.modules().on("nm_elite") ? plugin.nightmare().value("elite-chance") * plugin.events().eliteMult(le.getWorld()) : 0.0;
        if (MobTags.getType(le) == null || !MobTags.getType(le).equals("mini_zombie")) {
            if (roll(eliteChance)) {
                makeElite(le);
                track = true;
            }
        }

        if (track && le instanceof Mob m) plugin.ai().track(m, false);

        // Spawn más frecuente: refuerzos extra cuando aparece un mob natural
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL && !MobTags.isOurs(le) && plugin.modules().on("nm_spawn")) naturalExtras(le);
    }

    private void buff(LivingEntity le, PotionEffectType type, int level) {
        if (type == null || level <= 0) return;
        le.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, level - 1, true, false, false));
    }

    private void naturalExtras(LivingEntity le) {
        YamlConfiguration c = cfg();
        double extra = plugin.nightmare().value("mob-count") - 1.0 + c.getDouble("spawn.natural-extra", 0.0);
        if (extra <= 0) return;
        int n = (int) extra;
        if (roll(extra - n)) n++;
        n = Math.min(n, c.getInt("spawn.natural-extra-max", 3));
        if (n <= 0) return;
        Player nearest = nearestPlayer(le);
        if (nearest == null || plugin.perf().countOwner(nearest.getUniqueId()) >= plugin.perf().maxPerPlayer()) return;
        EntityType type = le.getType();
        java.util.UUID owner = nearest.getUniqueId();
        int count = n;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < count; i++) {
                var loc = plugin.spawns().spotNear(le.getLocation(), 6);
                if (plugin.spawns().spawnAt(type, loc, owner, null) == null) break;
            }
        });
    }

    private Player nearestPlayer(LivingEntity le) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : le.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(le.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ---- Zombies ----

    private boolean zombie(Zombie z) {
        YamlConfiguration c = cfg();
        boolean track = false;
        z.setCanBreakDoors(c.getBoolean("zombies.break-doors", true));
        z.setCanPickupItems(c.getBoolean("zombies.pick-up-items", true));

        if (z.isBaby() || roll(c.getDouble("zombies.mini-zombie-chance", 0.08))) {
            z.setBaby(true);
            MobTags.setType(z, "mini_zombie");
            mod(z, Attribute.MOVEMENT_SPEED, kSpeed, c.getDouble("zombies.mini-speed-bonus", 0.25));
        } else if (roll(c.getDouble("zombies.summoner-chance", 0.06))) {
            MobTags.setType(z, "zombie_summoner");
            z.customName(Component.text("Zombie Invocador", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
            z.setCustomNameVisible(true);
            mod(z, Attribute.MAX_HEALTH, kHealth, c.getDouble("zombies.summoner-health-bonus", 1.0));
            healFull(z);
            track = true;
        }

        // Armaduras y armas (con encantamientos)
        EntityEquipment eq = z.getEquipment();
        if (eq != null) {
            double armorChance = c.getDouble("zombies.armor-chance", 0.25) + plugin.nightmare().getLevel() * 0.01;
            double enchantChance = c.getDouble("zombies.enchant-chance", 0.3);
            int level = plugin.nightmare().getLevel();
            if (roll(armorChance)) {
                Material tier = level >= 12 ? Material.DIAMOND_HELMET : level >= 5 ? Material.IRON_HELMET : Material.CHAINMAIL_HELMET;
                String prefix = tier.name().substring(0, tier.name().indexOf('_'));
                eq.setHelmet(enchant(new ItemStack(tier), enchantChance, Enchantment.PROTECTION, 1 + level / 10));
                eq.setChestplate(enchant(new ItemStack(Material.valueOf(prefix + "_CHESTPLATE")), enchantChance, Enchantment.PROTECTION, 1 + level / 10));
                if (roll(0.5)) eq.setLeggings(new ItemStack(Material.valueOf(prefix + "_LEGGINGS")));
                if (roll(0.5)) eq.setBoots(new ItemStack(Material.valueOf(prefix + "_BOOTS")));
                eq.setHelmetDropChance(0.04f);
                eq.setChestplateDropChance(0.04f);
            }
            if (roll(c.getDouble("zombies.weapon-chance", 0.25))) {
                Material w = level >= 12 ? Material.DIAMOND_SWORD : level >= 5 ? Material.IRON_SWORD : Material.STONE_SWORD;
                eq.setItemInMainHand(enchant(new ItemStack(w), enchantChance, Enchantment.SHARPNESS, 1 + level / 8));
                eq.setItemInMainHandDropChance(0.04f);
            }
        }
        return track;
    }

    private ItemStack enchant(ItemStack item, double chance, Enchantment ench, int lvl) {
        if (roll(chance)) item.addUnsafeEnchantment(ench, Math.max(1, lvl));
        return item;
    }

    // ---- Arañas ----

    private boolean spider(LivingEntity le) {
        YamlConfiguration c = cfg();
        if (roll(c.getDouble("spiders.venom-chance", 0.12))) {
            MobTags.setType(le, "venom_spider");
            le.customName(Component.text("Araña Venenosa", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            le.setCustomNameVisible(true);
            mod(le, Attribute.MAX_HEALTH, kHealth, 0.5);
            healFull(le);
            return true;
        }
        return false;
    }

    // ---- Creepers ----

    private void creeper(Creeper cr) {
        YamlConfiguration c = cfg();
        double chargedChance = Math.min(c.getDouble("creepers.charged-max", 0.5),
                c.getDouble("creepers.charged-chance", 0.04) + c.getDouble("creepers.charged-per-level", 0.01) * (plugin.nightmare().getLevel() - 1));
        if (plugin.events().isActiveKey(cr.getWorld(), "creeper_storm")) chargedChance = Math.max(chargedChance, c.getDouble("creepers.storm-charged-chance", 0.6));
        if (roll(chargedChance)) {
            cr.setPowered(true);
            if (MobTags.getType(cr) == null) MobTags.setType(cr, "creeper_charged");
        }

        double r = ThreadLocalRandom.current().nextDouble();
        double acc = 0;
        for (String v : List.of("fast", "invisible", "giant", "fire", "big", "brood")) {
            acc += c.getDouble("creepers.variants." + v, 0.05);
            if (r < acc) {
                applyCreeperVariant(cr, v);
                return;
            }
        }
    }

    private void applyCreeperVariant(Creeper cr, String v) {
        YamlConfiguration c = cfg();
        MobTags.setType(cr, "creeper_" + v);
        switch (v) {
            case "fast" -> mod(cr, Attribute.MOVEMENT_SPEED, kSpeed, c.getDouble("creepers.fast-speed-bonus", 0.6));
            case "invisible" -> cr.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false, false));
            case "giant" -> {
                mod(cr, Attribute.SCALE, kScale, c.getDouble("creepers.giant-scale-bonus", 1.0));
                mod(cr, Attribute.MAX_HEALTH, kHealth, 1.0);
                healFull(cr);
                cr.setExplosionRadius(c.getInt("creepers.giant-radius", 6));
            }
            case "big" -> cr.setExplosionRadius(c.getInt("creepers.big-radius", 7));
            default -> { }
        }
        if (v.equals("fire") || v.equals("brood")) {
            cr.customName(Component.text(v.equals("fire") ? "Creeper Incendiario" : "Creeper Madre",
                    v.equals("fire") ? net.kyori.adventure.text.format.NamedTextColor.GOLD : net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
        }
    }

    // ---- Ghast Demoníaco (Nether) ----

    private boolean ghast(LivingEntity le) {
        YamlConfiguration c = cfg();
        if (!MobTags.isOurs(le) && !roll(c.getDouble("ghast.demonic-chance", 0.35))) return false;
        MobTags.setType(le, "ghast_demoniaco");
        le.customName(Component.text("Ghast Demoníaco", net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
        le.setCustomNameVisible(true);
        mod(le, Attribute.MAX_HEALTH, kHealth, c.getDouble("ghast.health", 60) / 10.0 - 1.0); // un ghast normal tiene 10
        mod(le, Attribute.SCALE, kScale, c.getDouble("ghast.scale", 1.6) - 1.0);
        healFull(le);
        return true;
    }

    // ---- Piglins siempre enojados ----

    private List<String> piglinTypes() {
        return cfg().getStringList("piglins.types");
    }

    /** Vuelve a registrar mobs especiales cargados desde disco (reinicios / chunks descargados). */
    public void readopt(Mob m) {
        if (!plugin.nightmare().worldEnabled(m.getWorld())) return;
        String type = MobTags.getType(m);
        if (type != null) {
            switch (type) {
                case "hostile_animal", "explosive_chicken", "enderman_hunter", "zombie_summoner", "piglin_angry", "ghast_demoniaco" ->
                        plugin.ai().track(m, true);
                default -> { }
            }
            return;
        }
        if (MobTags.isElite(m)) {
            plugin.ai().track(m, false);
            return;
        }
        if (plugin.modules().on("nm_piglins") && piglinTypes().contains(m.getType().name())) {
            MobTags.setType(m, "piglin_angry");
            plugin.ai().track(m, true);
        }
    }

    // ---- Esqueletos ----

    private boolean skeleton(AbstractSkeleton sk) {
        YamlConfiguration c = cfg();
        if (!roll(c.getDouble("skeletons.sniper-chance", 0.08))) return false;
        MobTags.setType(sk, "skeleton_sniper");
        sk.customName(Component.text("Skeleton Sniper", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        sk.setCustomNameVisible(true);
        mod(sk, Attribute.MAX_HEALTH, kHealth, c.getDouble("skeletons.sniper-health-bonus", 1.0));
        AttributeInstance fr = sk.getAttribute(Attribute.FOLLOW_RANGE);
        if (fr != null) fr.setBaseValue(Math.max(fr.getBaseValue(), c.getDouble("skeletons.sniper-range", 48)));
        healFull(sk);
        EntityEquipment eq = sk.getEquipment();
        if (eq != null) {
            ItemStack bow = new ItemStack(Material.BOW);
            bow.addUnsafeEnchantment(Enchantment.POWER, 3 + plugin.nightmare().getLevel() / 10);
            eq.setItemInMainHand(bow);
            eq.setItemInMainHandDropChance(0.02f);
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Golems, animales, pollos
    // ------------------------------------------------------------------

    private void golem(LivingEntity g) {
        YamlConfiguration c = cfg();
        mod(g, Attribute.MAX_HEALTH, kHealth, c.getDouble("golems.health-multiplier", 2.0) - 1.0);
        mod(g, Attribute.ATTACK_DAMAGE, kDamage, c.getDouble("golems.damage-multiplier", 2.0) - 1.0);
        int res = c.getInt("golems.resistance", 2);
        if (res > 0) buff(g, PotionEffectType.RESISTANCE, res);
        if (roll(c.getDouble("golems.elite-chance", 0.1))) {
            MobTags.setType(g, "golem_elite");
            g.customName(Component.text("Golem Élite", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            g.setCustomNameVisible(true);
            mod(g, Attribute.MAX_HEALTH, kHealth, c.getDouble("golems.elite-health-multiplier", 4.0) - 1.0);
            mod(g, Attribute.ATTACK_DAMAGE, kDamage, c.getDouble("golems.elite-damage-multiplier", 3.0) - 1.0);
            mod(g, Attribute.MOVEMENT_SPEED, kSpeed, c.getDouble("golems.elite-speed-bonus", 0.4));
            buff(g, PotionEffectType.RESISTANCE, c.getInt("golems.elite-resistance", 3));
            MobTags.setElite(g);
            if (g instanceof Mob m) plugin.ai().track(m, false);
        }
        healFull(g);
    }

    private void animal(Animals a, CreatureSpawnEvent.SpawnReason reason) {
        YamlConfiguration c = cfg();
        if (!c.getBoolean("animals.enabled", true)) return;
        boolean hostileOn = plugin.modules().on("nm_animals");
        boolean chickenOn = plugin.modules().on("nm_chicken");
        if (!hostileOn && !chickenOn) return;
        if (a instanceof Tameable t && t.isTamed()) return;
        if (!c.getStringList("animals.spawn-reasons").contains(reason.name())) return;
        List<String> types = c.getStringList("animals.types");
        if (!types.contains(a.getType().name())) return;

        boolean chicken = a.getType() == EntityType.CHICKEN;
        if (chicken && chickenOn && roll(c.getDouble("chicken.explosive-chance", 0.12) * plugin.nightmare().value("multiplication"))) {
            makeExplosiveChicken(a);
            return;
        }
        if (!hostileOn) return;
        double chance = plugin.nightmare().value("animal-hostile-chance");
        if (!roll(chance)) return;
        MobTags.setType(a, "hostile_animal");
        buff(a, PotionEffectType.STRENGTH, c.getInt("animals.strength", 2));
        buff(a, PotionEffectType.SPEED, c.getInt("animals.speed", 2));
        mod(a, Attribute.MAX_HEALTH, kHealth, plugin.nightmare().value("health") * c.getDouble("animals.health-multiplier", 1.5) - 1.0);
        healFull(a);
        if (a instanceof Mob m) plugin.ai().track(m, true);
    }

    /** Convierte un pollo en POLLO EXPLOSIVO (también lo usa el comando de prueba). */
    public void makeExplosiveChicken(LivingEntity a) {
        YamlConfiguration c = cfg();
        MobTags.setType(a, "explosive_chicken");
        a.customName(Component.text("Pollo Explosivo", net.kyori.adventure.text.format.NamedTextColor.RED));
        a.setCustomNameVisible(c.getBoolean("chicken.show-name", true));
        buff(a, PotionEffectType.SPEED, c.getInt("chicken.speed-level", 3));
        if (a instanceof Mob m) plugin.ai().track(m, true);
    }

    // ------------------------------------------------------------------
    //  Élites
    // ------------------------------------------------------------------

    public void makeElite(LivingEntity le) {
        YamlConfiguration c = cfg();
        MobTags.setElite(le);
        mod(le, Attribute.MAX_HEALTH, kHealth, c.getDouble("elite.health-bonus", 1.5) + getModAmount(le, kHealth));
        mod(le, Attribute.ATTACK_DAMAGE, kDamage, c.getDouble("elite.damage-bonus", 0.6));
        mod(le, Attribute.MOVEMENT_SPEED, kSpeed, c.getDouble("elite.speed-bonus", 0.15) + getModAmount(le, kSpeed));
        if (c.getBoolean("elite.glowing", true)) le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        buff(le, PotionEffectType.REGENERATION, c.getInt("elite.regeneration", 1));
        if (MobTags.getType(le) == null || !MobTags.getType(le).equals("zombie_summoner")) {
            le.customName(Component.text("★ Élite ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(Component.translatable(le.getType().translationKey())));
            le.setCustomNameVisible(true);
        }
        healFull(le);
    }

    private double getModAmount(LivingEntity le, NamespacedKey key) {
        Attribute attr = key.equals(kHealth) ? Attribute.MAX_HEALTH : Attribute.MOVEMENT_SPEED;
        AttributeInstance inst = le.getAttribute(attr);
        if (inst == null) return 0;
        for (AttributeModifier m : inst.getModifiers()) if (key.equals(m.getKey())) return m.getAmount();
        return 0;
    }

    // ------------------------------------------------------------------
    //  Utilidades
    // ------------------------------------------------------------------

    private void mod(LivingEntity le, Attribute attr, NamespacedKey key, double amount) {
        plugin.mobs().setModifier(le, attr, key, amount);
    }

    private void healFull(LivingEntity le) {
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) le.setHealth(hp.getValue());
    }

    /** Nombre bonito de tipo para mensajes de depuración. */
    public static String pretty(EntityType t) {
        String n = t.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
