package com.extreme.nightmare;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Recompensas configurables con el formato  MATERIAL:min-max@probabilidad  (ej: DIAMOND:1-3@0.25). */
public final class Drops {

    public record Drop(Material material, int min, int max, double chance) {}

    private Drops() {}

    public static List<Drop> parse(List<String> raw) {
        List<Drop> out = new ArrayList<>();
        for (String line : raw) {
            try {
                String chancePart = "1";
                String main = line;
                if (line.contains("@")) {
                    main = line.substring(0, line.indexOf('@'));
                    chancePart = line.substring(line.indexOf('@') + 1);
                }
                String[] parts = main.split(":");
                Material m = Material.matchMaterial(parts[0].trim());
                if (m == null) continue;
                int min = 1, max = 1;
                if (parts.length > 1) {
                    String[] range = parts[1].split("-");
                    min = Integer.parseInt(range[0].trim());
                    max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
                }
                out.add(new Drop(m, Math.max(1, min), Math.max(min, max), Double.parseDouble(chancePart.trim())));
            } catch (RuntimeException ignored) {
                // línea inválida: se ignora
            }
        }
        return out;
    }

    public static void roll(List<Drop> drops, Location loc, double chanceMultiplier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Drop d : drops) {
            if (r.nextDouble() >= Math.min(1.0, d.chance() * chanceMultiplier)) continue;
            int amount = d.min() == d.max() ? d.min() : r.nextInt(d.min(), d.max() + 1);
            loc.getWorld().dropItemNaturally(loc, new ItemStack(d.material(), amount));
        }
    }
}
