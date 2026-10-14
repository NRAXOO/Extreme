package com.extreme.config;

import com.extreme.ExtremePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Estado interno que debe sobrevivir a reinicios (state.yml): inicio del día 1, último día anunciado, temporizador de PVP. */
public class StateStore {

    private final ExtremePlugin plugin;
    private final File file;

    public long dayStart;
    public int lastDay;
    public boolean maxAnnounced;
    public long nextPvpToggle;
    public int nightmareOverride;      // 0 = automático
    public int lastNightmareLevel;
    public long lastWorldBossDay;

    public StateStore(ExtremePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    public void load() {
        YamlConfiguration y = plugin.io().load(file);
        dayStart = y.getLong("day-start", 0L);
        lastDay = y.getInt("last-day", 0);
        maxAnnounced = y.getBoolean("max-announced", false);
        nextPvpToggle = y.getLong("next-pvp-toggle", 0L);
        nightmareOverride = y.getInt("nightmare-override", 0);
        lastNightmareLevel = y.getInt("last-nightmare-level", 0);
        lastWorldBossDay = y.getLong("last-world-boss-day", 0L);
        if (dayStart <= 0) {
            dayStart = System.currentTimeMillis();
            save();
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("day-start", dayStart);
        y.set("last-day", lastDay);
        y.set("max-announced", maxAnnounced);
        y.set("next-pvp-toggle", nextPvpToggle);
        y.set("nightmare-override", nightmareOverride);
        y.set("last-nightmare-level", lastNightmareLevel);
        y.set("last-world-boss-day", lastWorldBossDay);
        plugin.io().saveAsync(file, y.saveToString());
    }
}
