package com.extreme.config;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * Acceso a messages.yml. Los placeholders se pasan en pares: send(to, "clave", "player", "Steve", "day", 5)
 * Una clave con valor "" se considera silenciada y no envía nada.
 */
public class Messages {

    private final ExtremePlugin plugin;

    public Messages(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get("messages.yml");
    }

    public String raw(String key) {
        YamlConfiguration c = cfg();
        if (c.isList(key)) return String.join("\n", c.getStringList(key));
        String s = c.getString(key);
        return s == null ? "&c[mensaje no encontrado: " + key + "]" : s;
    }

    private List<String> rawLines(String key) {
        YamlConfiguration c = cfg();
        return c.isList(key) ? c.getStringList(key) : List.of(raw(key));
    }

    public String format(String text, Object... pairs) {
        String prefix = cfg().getString("prefix");
        String out = text.replace("{prefix}", prefix == null ? "" : prefix);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return out;
    }

    /** Un solo texto con placeholders (las listas se unen con saltos de línea). */
    public String str(String key, Object... pairs) {
        return format(raw(key), pairs);
    }

    /** Lista de líneas con placeholders. */
    public List<String> list(String key, Object... pairs) {
        return rawLines(key).stream().map(l -> format(l, pairs)).toList();
    }

    public Component comp(String key, Object... pairs) {
        return Text.color(str(key, pairs));
    }

    public void send(CommandSender to, String key, Object... pairs) {
        if (raw(key).isEmpty()) return;
        for (String line : list(key, pairs)) to.sendMessage(Text.color(line));
    }

    public void broadcast(String key, Object... pairs) {
        if (raw(key).isEmpty()) return;
        for (String line : list(key, pairs)) Bukkit.getServer().broadcast(Text.color(line));
    }

    /** "&aACTIVADO" / "&cDESACTIVADO" según messages.yml. */
    public String state(boolean on) {
        return str(on ? "states.on" : "states.off");
    }
}
