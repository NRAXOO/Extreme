package com.extreme.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Effects {

    private Effects() {}

    /** Acepta nombres modernos (poison, resistance) y legacy (DAMAGE_RESISTANCE). */
    @SuppressWarnings("deprecation")
    public static PotionEffectType parse(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        PotionEffectType t = null;
        try {
            t = Registry.EFFECT.get(NamespacedKey.minecraft(n));
        } catch (IllegalArgumentException ignored) {
            // nombre con caracteres inválidos
        }
        if (t == null) t = PotionEffectType.getByName(n.toUpperCase(Locale.ROOT));
        return t;
    }

    /** Efecto configurable con el formato  NOMBRE:ticks:amplificador@probabilidad  (ej: POISON:100:0@0.3). */
    public record Spec(PotionEffectType type, int ticks, int amplifier, double chance) {
        public org.bukkit.potion.PotionEffect create() {
            return new org.bukkit.potion.PotionEffect(type, ticks, amplifier, false, true, true);
        }
    }

    public static List<Spec> parseSpecs(List<String> raw) {
        List<Spec> out = new ArrayList<>();
        for (String line : raw) {
            try {
                double chance = 1.0;
                String main = line;
                if (line.contains("@")) {
                    chance = Double.parseDouble(line.substring(line.indexOf('@') + 1).trim());
                    main = line.substring(0, line.indexOf('@'));
                }
                String[] p = main.split(":");
                PotionEffectType t = parse(p[0]);
                if (t == null) continue;
                out.add(new Spec(t, p.length > 1 ? Integer.parseInt(p[1].trim()) : 100,
                        p.length > 2 ? Integer.parseInt(p[2].trim()) : 0, chance));
            } catch (RuntimeException ignored) {
                // línea inválida
            }
        }
        return out;
    }
}
