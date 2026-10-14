package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Ambientación: títulos, sonidos, partículas y action bar. Todo con nombres configurables. */
public final class Fx {

    private static final Map<String, Particle> PARTICLES = new HashMap<>();

    private Fx() {}

    public static Particle particle(String name) {
        if (name == null) return null;
        String key = name.trim().toUpperCase(Locale.ROOT);
        Particle cached = PARTICLES.get(key);
        if (cached != null) return cached;
        try {
            Particle p = Particle.valueOf(key);
            PARTICLES.put(key, p);
            return p;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void particle(Location loc, String name, int count, double spread, double speed) {
        Particle p = particle(name);
        if (p == null || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(p, loc, count, spread, spread, spread, speed);
    }

    public static void sound(ExtremePlugin plugin, Location loc, String name, float volume, float pitch) {
        Sound s = plugin.sounds().resolve(name);
        if (s == null || loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, s, SoundCategory.HOSTILE, volume, pitch);
    }

    public static void soundTo(ExtremePlugin plugin, Player p, String name, float volume, float pitch) {
        Sound s = plugin.sounds().resolve(name);
        if (s != null) p.playSound(p.getLocation(), s, SoundCategory.MASTER, volume, pitch);
    }

    public static void title(Player p, String title, String subtitle) {
        p.showTitle(Title.title(Text.color(title), Text.color(subtitle),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))));
    }

    public static void title(Collection<? extends Player> players, String title, String subtitle) {
        Component t = Text.color(title), s = Text.color(subtitle);
        Title ti = Title.title(t, s, Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000)));
        for (Player p : players) p.showTitle(ti);
    }

    public static void actionBar(Player p, String text) {
        p.sendActionBar(Text.color(text));
    }

    public static void chat(Collection<? extends Player> players, String text) {
        Component c = Text.color(text);
        for (Player p : players) p.sendMessage(c);
    }

    public static void broadcast(String text) {
        Bukkit.getServer().broadcast(Text.color(text));
    }
}
