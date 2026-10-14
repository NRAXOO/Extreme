package com.extreme.utils;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Sonidos configurables (config.yml -> sounds). Acepta ENTITY_WITHER_SPAWN o entity.wither.spawn. */
public class SoundManager {

    private record Spec(boolean enabled, Sound sound, float volume, float pitch) {}

    private final ExtremePlugin plugin;
    private final Map<String, Spec> specs = new HashMap<>();
    private Map<String, Sound> lookup;

    public SoundManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        specs.clear();
        ConfigurationSection root = plugin.configs().get("config.yml").getConfigurationSection("sounds");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Sound sound = resolve(s.getString("sound", ""));
            if (sound == null && s.getBoolean("enabled", true)) {
                plugin.getLogger().warning("Sonido inválido en config.yml (sounds." + key + "): " + s.getString("sound"));
            }
            specs.put(key, new Spec(s.getBoolean("enabled", true) && sound != null, sound,
                    (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0)));
        }
    }

    public Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (n.contains(".") || n.contains(":")) {
            try {
                NamespacedKey key = NamespacedKey.fromString(n.toLowerCase(Locale.ROOT));
                return key == null ? null : Registry.SOUNDS.get(key);
            } catch (Exception e) {
                return null;
            }
        }
        if (lookup == null) {
            lookup = new HashMap<>();
            for (Sound s : Registry.SOUNDS) {
                lookup.put(s.getKey().getKey().toUpperCase(Locale.ROOT).replace('.', '_'), s);
            }
        }
        return lookup.get(n.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /** Reproduce el sonido para todos los jugadores conectados. */
    public void playAll(String key) {
        Spec s = specs.get(key);
        if (s == null || !s.enabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), s.sound(), SoundCategory.MASTER, s.volume(), s.pitch());
        }
    }

    public void playTo(Player p, String key) {
        Spec s = specs.get(key);
        if (s == null || !s.enabled()) return;
        p.playSound(p.getLocation(), s.sound(), SoundCategory.MASTER, s.volume(), s.pitch());
    }
}
