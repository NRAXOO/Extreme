package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Nivel de Pesadilla GLOBAL. Se calcula a partir del día del mundo (sin tope si uncapped-days)
 * y escala todos los sistemas mediante value(clave). Una sola tarea de comprobación cada minuto.
 */
public class NightmareManager {

    private record Scale(double base, double perLevel, double max) {}

    private final ExtremePlugin plugin;
    private final Map<String, Scale> scaling = new HashMap<>();
    private BukkitTask task;
    private int level = 1;
    private double detectionBase = 30, detectionPerLevel = 1, detectionMaxExtra = 34;
    private Set<String> worlds = new HashSet<>();

    public NightmareManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get("nightmare.yml");
    }

    public void reload() {
        YamlConfiguration c = cfg();
        scaling.clear();
        ConfigurationSection s = c.getConfigurationSection("nightmare.scaling");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                ConfigurationSection e = s.getConfigurationSection(key);
                if (e == null) continue;
                scaling.put(key, new Scale(e.getDouble("base", 1.0), e.getDouble("per-level", 0.0), e.getDouble("max", 1000.0)));
            }
        }
        detectionBase = c.getDouble("mobs.detection-range", 30);
        detectionPerLevel = c.getDouble("nightmare.detection.per-level", 1.0);
        detectionMaxExtra = c.getDouble("nightmare.detection.max-extra", 34);
        worlds = new HashSet<>(c.getStringList("nightmare.worlds"));

        level = computeLevel();
        plugin.state().lastNightmareLevel = level;

        if (task != null) task.cancel();
        long ticks = Math.max(200L, c.getLong("nightmare.check-interval", 60) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, ticks, ticks);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public boolean isEnabled() {
        return plugin.modules().on("nightmare") && cfg().getBoolean("nightmare.enabled", true);
    }

    public boolean worldEnabled(World w) {
        return isEnabled() && (worlds.isEmpty() || worlds.contains(w.getName()));
    }

    public int getLevel() { return level; }

    /** Valor escalado por nivel: base + perLevel*(nivel-1), limitado por max. */
    public double value(String key) {
        Scale s = scaling.get(key);
        if (s == null) return 1.0;
        return Math.min(s.max(), s.base() + s.perLevel() * (level - 1));
    }

    /** Igual que value() pero devuelve 1.0 si la Pesadilla está desactivada (para multiplicadores). */
    public double mult(String key) {
        return isEnabled() ? value(key) : 1.0;
    }

    /** Distancia de detección en bloques según el nivel. */
    public double detection() {
        return detectionBase + Math.min(detectionMaxExtra, detectionPerLevel * (level - 1));
    }

    public int computeLevel() {
        YamlConfiguration c = cfg();
        int max = Math.max(1, c.getInt("nightmare.max-level", 100));
        int override = plugin.state().nightmareOverride;
        if (override > 0) return Math.min(override, max);

        int day = c.getBoolean("nightmare.uncapped-days", true) ? plugin.days().getRawDay() : plugin.days().getDay();
        int start = Math.max(1, c.getInt("nightmare.starting-level", 1));
        int lvl;
        if ("LINEAR".equalsIgnoreCase(c.getString("nightmare.mode", "TABLE"))) {
            lvl = start + (day - 1) / Math.max(1, c.getInt("nightmare.days-per-level", 5));
        } else {
            List<Integer> table = c.getIntegerList("nightmare.table-days");
            int idx = 0;
            for (int i = 0; i < table.size(); i++) if (day >= table.get(i)) idx = i;
            lvl = start + idx;
            if (!table.isEmpty() && c.getBoolean("nightmare.continue-after-table", true) && day >= table.get(table.size() - 1)) {
                lvl += (day - table.get(table.size() - 1)) / Math.max(1, c.getInt("nightmare.beyond-table-days-per-level", 20));
            }
        }
        return Math.max(1, Math.min(lvl, max));
    }

    public String levelName(int lvl) {
        List<String> names = cfg().getStringList("nightmare.table-names");
        int start = Math.max(1, cfg().getInt("nightmare.starting-level", 1));
        int idx = lvl - start;
        if (names.isEmpty()) return String.valueOf(lvl);
        if (idx < 0) return names.get(0);
        if (idx < names.size()) return names.get(idx);
        return names.get(names.size() - 1) + " +" + (idx - names.size() + 1);
    }

    public String levelName() { return levelName(level); }

    /** Fija el nivel manualmente (0 = volver al cálculo automático). */
    public void setOverride(int lvl) {
        plugin.state().nightmareOverride = Math.max(0, lvl);
        plugin.state().save();
        int old = level;
        level = computeLevel();
        plugin.state().lastNightmareLevel = level;
        if (level > old) announce();
    }

    private void check() {
        if (!isEnabled()) return;
        int l = computeLevel();
        if (l != level) {
            boolean up = l > level;
            level = l;
            plugin.state().lastNightmareLevel = l;
            plugin.state().save();
            if (up) announce();
        }
        plugin.bosses().checkWorldBossSchedule(plugin.days().getRawDay());
    }

    private void announce() {
        YamlConfiguration c = cfg();
        String name = levelName();
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        Fx.broadcast(c.getString("nightmare.level-up.chat", "&4La Pesadilla aumenta...").replace("{name}", name).replace("{level}", String.valueOf(level)));
        Fx.title(all, c.getString("nightmare.level-up.title", "&4&l⚠ PESADILLA {name} ⚠").replace("{name}", name),
                c.getString("nightmare.level-up.subtitle", "&cEl mundo se vuelve más cruel...").replace("{level}", String.valueOf(level)));
        for (Player p : all) Fx.soundTo(plugin, p, c.getString("nightmare.level-up.sound", "ENTITY_WITHER_SPAWN"), 0.8f, 0.7f);
    }

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add("&8&m        &r &4☠ Pesadilla &8&m        ");
        out.add("&7Nivel: &c" + level + " &8(&4" + levelName() + "&8)" + (plugin.state().nightmareOverride > 0 ? " &e[manual]" : ""));
        out.add("&7Día real: &f" + plugin.days().getRawDay());
        for (String k : List.of("health", "damage", "speed", "spawn-rate", "mob-count", "event-frequency", "acid-intensity")) {
            out.add("&7" + k + ": &fx" + String.format(Locale.ROOT, "%.2f", value(k)));
        }
        out.add(String.format(Locale.ROOT, "&7elite: &f%.1f%% &8| &7mini-jefe: &f%.2f%% &8| &7detección: &f%.0f bloques",
                value("elite-chance") * 100, value("miniboss-chance") * 100, detection()));
        return out;
    }
}
