package com.extreme.difficulty;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Effects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Dificultad MANUAL: un único perfil que se edita desde el panel (/admin -> Dificultad) o difficulty.yml.
 * Ya no cambia sola con los días.
 */
public class DifficultyManager {

    private final ExtremePlugin plugin;
    private DifficultyProfile manual = DifficultyProfile.IDENTITY;
    private List<PotionEffectType> buffTypes = List.of();
    private int buffCount = 1;

    public DifficultyManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("difficulty.yml");
        String p = "difficulty.manual.";
        manual = new DifficultyProfile(1,
                c.getDouble(p + "mob-damage", 1.0),
                c.getDouble(p + "mob-health", 1.0),
                c.getDouble(p + "mob-speed", 1.0),
                c.getDouble(p + "mob-follow-range", 1.0),
                c.getDouble(p + "spider-cobweb-chance", -1),
                c.getDouble(p + "effect-chance-bonus", 0),
                c.getDouble(p + "effect-duration-multiplier", 1.0),
                c.getDouble(p + "buff-chance", 0),
                c.getInt(p + "buff-amplifier", 0),
                c.getString(p + "label", "&eManual"));

        buffCount = Math.max(1, c.getInt("difficulty.buffs.count", 1));
        List<PotionEffectType> types = new ArrayList<>();
        for (String n : c.getStringList("difficulty.buffs.effects")) {
            PotionEffectType t = Effects.parse(n);
            if (t == null) plugin.getLogger().warning("Efecto de buff desconocido en difficulty.yml: " + n);
            else types.add(t);
        }
        buffTypes = types;
    }

    public boolean isEnabled() {
        return plugin.configs().get("difficulty.yml").getBoolean("difficulty.enabled", false);
    }

    /** Perfil activo. Con la dificultad manual desactivada no cambia nada (IDENTITY). */
    public DifficultyProfile getProfile() {
        return isEnabled() ? manual : DifficultyProfile.IDENTITY;
    }

    public DifficultyProfile getManual() { return manual; }

    /** Día real del servidor (solo se usa para condiciones "minimum-day" configuradas a mano). */
    public int effectiveDay() {
        return plugin.days().getDay();
    }

    public String getLabel() {
        return isEnabled() ? manual.label()
                : plugin.configs().get("actionbar.yml").getString("actionbar.values.difficulty-off", "&7Desactivada");
    }

    public List<PotionEffectType> getBuffTypes() { return buffTypes; }

    public int getBuffCount() { return buffCount; }

    public void setEnabled(boolean on) {
        if (on == isEnabled()) return;
        plugin.configs().set("difficulty.yml", "difficulty.enabled", on);
        plugin.messages().broadcast(on ? "difficulty-enabled" : "difficulty-disabled");
        plugin.sounds().playAll(on ? "difficulty-enabled" : "difficulty-disabled");
    }

    /** Cambia un valor del perfil manual (comando /difficulty set). */
    public void setValue(String key, double value) {
        plugin.configs().set("difficulty.yml", "difficulty.manual." + key, value);
        reload();
    }

    public void resetManual() {
        for (String k : List.of("mob-damage", "mob-health", "mob-speed", "mob-follow-range", "effect-duration-multiplier")) {
            plugin.configs().set("difficulty.yml", "difficulty.manual." + k, 1.0);
        }
        plugin.configs().set("difficulty.yml", "difficulty.manual.spider-cobweb-chance", -1);
        plugin.configs().set("difficulty.yml", "difficulty.manual.effect-chance-bonus", 0);
        plugin.configs().set("difficulty.yml", "difficulty.manual.buff-chance", 0);
        plugin.configs().set("difficulty.yml", "difficulty.manual.buff-amplifier", 0);
        reload();
    }
}
