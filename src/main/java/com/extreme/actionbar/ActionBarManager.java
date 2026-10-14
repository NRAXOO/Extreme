package com.extreme.actionbar;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ActionBar con UNA sola tarea repetitiva. Los placeholders globales se calculan una vez por ciclo
 * y el Component final se cachea por número de vidas, así que el coste no crece con los jugadores.
 */
public class ActionBarManager {

    private final ExtremePlugin plugin;
    private BukkitTask task;
    private String template = "";
    private Set<String> disabledWorlds = new HashSet<>();
    private String pvpOn, pvpOff, randomOn, randomOff, livesDisabled, separator = " &8| ", revengeFormat;

    public ActionBarManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        shutdown();
        YamlConfiguration c = plugin.configs().get("actionbar.yml");
        if (!c.getBoolean("actionbar.enabled", true)) return;

        pvpOn = c.getString("actionbar.values.pvp-on", "&aACTIVADO");
        pvpOff = c.getString("actionbar.values.pvp-off", "&cDESACTIVADO");
        randomOn = c.getString("actionbar.values.random-on", "&aON");
        randomOff = c.getString("actionbar.values.random-off", "&cOFF");
        livesDisabled = c.getString("actionbar.values.lives-disabled", "∞");
        disabledWorlds = new HashSet<>(c.getStringList("actionbar.disabled-worlds"));
        separator = c.getString("actionbar.separator", " &8| ");
        revengeFormat = c.getBoolean("actionbar.revenge.enabled", true) ? c.getString("actionbar.revenge.format", "&4⚔ VENGANZA: &c{killer} &7({time})") : null;
        template = buildTemplate(c);

        long interval = Math.max(5L, c.getLong("actionbar.update-interval", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    public boolean isRunning() { return task != null; }

    private String buildTemplate(YamlConfiguration c) {
        if (!"ELEMENTS".equalsIgnoreCase(c.getString("actionbar.mode", "ELEMENTS"))) {
            return c.getString("actionbar.format", "");
        }
        String sep = c.getString("actionbar.separator", " &8| ");
        List<String> parts = new ArrayList<>();
        for (String name : c.getStringList("actionbar.order")) {
            ConfigurationSection e = c.getConfigurationSection("actionbar.elements." + name);
            if (e != null && e.getBoolean("enabled", true)) parts.add(e.getString("format", ""));
        }
        return String.join(sep, parts);
    }

    private void tick() {
        if (template.isEmpty()) return;
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) return;

        boolean pvp = plugin.pvp().isEnabled();
        String base = template
                .replace("{pvp}", pvp ? pvpOn : pvpOff)
                .replace("{pvp_random}", plugin.pvp().isRandomEnabled() ? randomOn : randomOff)
                .replace("{pvp_timer}", plugin.pvp().timerText())
                .replace("{day}", String.valueOf(plugin.days().getDay()))
                .replace("{max_day}", String.valueOf(plugin.days().getMaxDays()))
                .replace("{difficulty}", plugin.difficulty().getLabel());

        boolean livesOn = plugin.lives().isEnabled();
        String maxLives = livesOn ? String.valueOf(plugin.lives().getMaxLives()) : livesDisabled;
        base = base.replace("{max_lives}", maxLives);

        Map<String, Component> cache = new HashMap<>();
        for (Player p : players) {
            if (!disabledWorlds.isEmpty() && disabledWorlds.contains(p.getWorld().getName())) continue;
            int lives = livesOn ? plugin.lives().getLives(p.getUniqueId()) : -1;
            final String b = base;
            String rev = null;
            if (revengeFormat != null && plugin.revenge().isEnabled()) {
                var r = plugin.revenge().get(p.getUniqueId());
                if (r != null) {
                    rev = revengeFormat.replace("{killer}", r.killerName())
                            .replace("{time}", com.extreme.utils.TimeUtil.clock(plugin.revenge().remainingMs(p.getUniqueId())));
                }
            }
            final String revText = rev;
            String cacheKey = lives + "|" + (revText == null ? "" : revText);
            Component c = cache.computeIfAbsent(cacheKey, k -> Text.color(
                    b.replace("{lives}", lives < 0 ? livesDisabled : String.valueOf(lives))
                     .replace("{hearts}", lives < 0 ? livesDisabled : "❤".repeat(Math.min(lives, 30)))
                     + (revText == null ? "" : separator + revText)));
            p.sendActionBar(c);
        }
    }
}
