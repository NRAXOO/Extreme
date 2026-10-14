package com.extreme.mobs;

import com.extreme.ExtremePlugin;
import com.extreme.difficulty.DifficultyProfile;
import com.extreme.utils.Effects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Efectos (veneno, hambre, lentitud...) que los mobs aplican al golpear. Todo en mobs.yml -> mob-effects. */
public class MobEffectsManager {

    private record Rule(PotionEffectType type, double chance, int durationSec, int amplifier, int minDay) {}

    private final ExtremePlugin plugin;
    private final Map<EntityType, List<Rule>> rules = new EnumMap<>(EntityType.class);
    private boolean enabled = true;

    public MobEffectsManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        rules.clear();
        YamlConfiguration c = plugin.configs().get("mobs.yml");
        enabled = c.getBoolean("mob-effects.enabled", true);
        ConfigurationSection root = c.getConfigurationSection("mob-effects");
        if (root == null) return;

        for (String mobKey : root.getKeys(false)) {
            ConfigurationSection mob = root.getConfigurationSection(mobKey);
            if (mob == null) continue;
            EntityType type;
            try {
                type = EntityType.valueOf(mobKey.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Mob desconocido en mob-effects: " + mobKey);
                continue;
            }
            List<Rule> list = new ArrayList<>();
            for (String effectKey : mob.getKeys(false)) {
                ConfigurationSection s = mob.getConfigurationSection(effectKey);
                if (s == null || !s.getBoolean("enabled", true)) continue;
                PotionEffectType pet = Effects.parse(effectKey);
                if (pet == null) {
                    plugin.getLogger().warning("Efecto desconocido en mob-effects." + mobKey + ": " + effectKey);
                    continue;
                }
                list.add(new Rule(pet, s.getDouble("chance", 0), s.getInt("duration", 5),
                        s.getInt("amplifier", 0), s.getInt("minimum-day", 1)));
            }
            if (!list.isEmpty()) rules.put(type, list);
        }
    }

    /** Llamado cuando un mob golpea a un jugador. */
    public void onHit(LivingEntity source, Player target) {
        if (!enabled || !plugin.modules().on("mob_effects")) return;
        List<Rule> list = rules.get(source.getType());
        if (list == null) return;

        int day = plugin.difficulty().effectiveDay();
        DifficultyProfile profile = plugin.difficulty().getProfile();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (Rule r : list) {
            if (r.chance() <= 0 || day < r.minDay()) continue;
            double chance = Math.min(100.0, r.chance() + profile.effectChanceBonus());
            if (rnd.nextDouble() * 100.0 >= chance) continue;
            int ticks = (int) Math.max(20, Math.round(r.durationSec() * 20.0 * profile.effectDurationMultiplier()));
            target.addPotionEffect(new PotionEffect(r.type(), ticks, Math.max(0, r.amplifier()), true, true, true));
        }
    }
}
