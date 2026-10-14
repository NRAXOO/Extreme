package com.extreme.commands;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CommandUtil {

    private CommandUtil() {}

    /** Filtra sugerencias por lo que el usuario ya escribió. */
    static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }

    static Boolean parseBool(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "on", "true", "si", "sí", "yes", "activar" -> true;
            case "off", "false", "no", "desactivar" -> false;
            default -> null;
        };
    }

    static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean has(CommandSender s, String perm) {
        return s.hasPermission(perm) || s.hasPermission("extreme.admin");
    }
}
