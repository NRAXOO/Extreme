package com.extreme.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Lectura y escritura de archivos de datos (players.yml, state.yml).
 * Las escrituras van a un hilo propio (en orden) y son atómicas para evitar archivos corruptos.
 */
public final class DataFile {

    private final JavaPlugin plugin;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Extreme-IO");
        t.setDaemon(true);
        return t;
    });

    public DataFile(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration load(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) return yaml;
        try {
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
            boolean moved = file.renameTo(backup);
            plugin.getLogger().log(Level.SEVERE, "No se pudo leer " + file.getName() + " (" + e.getMessage()
                    + "). " + (moved ? "Se guardó una copia en " + backup.getName() + ". " : "") + "Se empieza con datos vacíos.");
            return new YamlConfiguration();
        }
        return yaml;
    }

    public void saveAsync(File file, String content) {
        io.execute(() -> write(file, content));
    }

    private void write(File file, String content) {
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + file.getName(), e);
        }
    }

    /** Espera a que terminen todas las escrituras pendientes. */
    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
