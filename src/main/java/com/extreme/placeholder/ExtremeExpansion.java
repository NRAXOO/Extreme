package com.extreme.placeholder;

import com.extreme.ExtremePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

/** %extreme_pvp% %extreme_day% %extreme_lives% %extreme_max_lives% %extreme_difficulty% %extreme_pvp_random% ... */
public class ExtremeExpansion extends PlaceholderExpansion {

    private final ExtremePlugin plugin;

    public ExtremeExpansion(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "extreme"; }

    @Override
    public String getAuthor() { return "Extreme"; }

    @Override
    public String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        YamlConfiguration c = plugin.configs().get("config.yml");
        String on = c.getString("placeholders.on", "ACTIVADO");
        String off = c.getString("placeholders.off", "DESACTIVADO");
        switch (params.toLowerCase()) {
            case "pvp": return plugin.pvp().isEnabled() ? on : off;
            case "pvp_random": return plugin.pvp().isRandomEnabled() ? on : off;
            case "pvp_timer": return plugin.pvp().timerText();
            case "day": return String.valueOf(plugin.days().getDay());
            case "max_day": return String.valueOf(plugin.days().getMaxDays());
            case "max_lives": return String.valueOf(plugin.lives().getMaxLives());
            case "lives":
                if (player == null) return "";
                return String.valueOf(plugin.lives().getLives(player.getUniqueId()));
            case "difficulty":
                return plugin.difficulty().isEnabled()
                        ? com.extreme.utils.Text.plain(plugin.difficulty().getLabel())
                        : c.getString("placeholders.difficulty-disabled", "Desactivada");
            case "worldboss_timer": {
                if (plugin.bosses().isCountdownActive()) return com.extreme.utils.TimeUtil.clock(plugin.bosses().countdownRemainingMs());
                long next = plugin.bosses().nextScheduledMs();
                return next < 0 ? "" : com.extreme.utils.TimeUtil.format(next);
            }
            case "worldboss_coords": return plugin.bosses().countdownCoords();
            case "nightmare_level": return String.valueOf(plugin.nightmare().getLevel());
            case "nightmare_name": return plugin.nightmare().levelName();
            case "revenge":
                if (player == null || plugin.revenge().get(player.getUniqueId()) == null) return "";
                return com.extreme.utils.TimeUtil.clock(plugin.revenge().remainingMs(player.getUniqueId()));
            case "revenge_target":
                if (player == null || plugin.revenge().get(player.getUniqueId()) == null) return "";
                return plugin.revenge().get(player.getUniqueId()).killerName();
            default: return null;
        }
    }
}
