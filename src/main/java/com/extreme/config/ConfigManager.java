package com.extreme.config;

import com.extreme.ExtremePlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carga y recarga segura de todos los archivos de configuración.
 * Un error de sintaxis en un YAML nunca tumba el plugin: se informa archivo y línea
 * y se conserva la configuración anterior.
 */
public class ConfigManager {

    public static final List<String> FILES = List.of(
            "config.yml", "messages.yml", "pvp.yml", "difficulty.yml", "lives.yml", "actionbar.yml", "mobs.yml", "nightmare.yml", "revenge.yml", "modules.yml");

    public record ConfigError(String file, int line, String message) {
        public String describe() {
            return line > 0 ? file + " (línea " + line + "): " + message : file + ": " + message;
        }
    }

    private static final Pattern LINE = Pattern.compile("line (\\d+)");

    private final ExtremePlugin plugin;
    private volatile Map<String, YamlConfiguration> configs = new HashMap<>();
    /** Archivos que cayeron a los valores por defecto del jar: no se deben sobrescribir al guardar. */
    private final Set<String> broken = new HashSet<>();

    public ConfigManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param strict true = si hay algún error NO se reemplaza nada (recarga). false = arranque: los archivos
     *               rotos se sustituyen en memoria por los valores por defecto del jar.
     */
    public List<ConfigError> load(boolean strict) {
        Map<String, YamlConfiguration> fresh = new HashMap<>();
        List<ConfigError> errors = new ArrayList<>();
        Set<String> brokenNow = new HashSet<>();

        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) plugin.saveResource(name, false);
            YamlConfiguration defaults = loadDefaults(name);
            YamlConfiguration cfg = new YamlConfiguration();
            try {
                cfg.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                if (defaults != null) cfg.setDefaults(defaults);
                fresh.put(name, cfg);
            } catch (InvalidConfigurationException | IOException e) {
                errors.add(new ConfigError(name, extractLine(e), shortMessage(e)));
                if (!strict) {
                    fresh.put(name, defaults != null ? defaults : new YamlConfiguration());
                    brokenNow.add(name);
                }
            }
        }

        if (errors.isEmpty() || !strict) {
            configs = fresh;
            broken.clear();
            broken.addAll(brokenNow);
        }
        return errors;
    }

    private YamlConfiguration loadDefaults(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private int extractLine(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() == null) continue;
            Matcher m = LINE.matcher(c.getMessage());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private String shortMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }

    public YamlConfiguration get(String name) {
        YamlConfiguration c = configs.get(name);
        return c != null ? c : new YamlConfiguration();
    }

    /** Cambia un valor y guarda el archivo. */
    public void set(String name, String path, Object value) {
        YamlConfiguration c = configs.get(name);
        if (c == null) return;
        c.set(path, value);
        save(name);
    }

    public void save(String name) {
        YamlConfiguration c = configs.get(name);
        if (c == null) return;
        if (broken.contains(name)) {
            plugin.getLogger().warning(name + " tiene errores de sintaxis: no se sobrescribe. Corrígelo y usa /extreme reload.");
            return;
        }
        try {
            c.save(new File(plugin.getDataFolder(), name));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + name, e);
        }
    }
}
