package com.extreme.commands;

import com.extreme.ExtremePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** /pvp [activar|desactivar|estado|aleatorio activar|desactivar] */
public class PvpCommand implements CommandExecutor, TabCompleter {

    private final ExtremePlugin plugin;

    public PvpCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "estado" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "estado" -> {
                if (!CommandUtil.has(sender, "extreme.pvp.status")) return deny(sender);
                plugin.messages().send(sender, "pvp-status",
                        "state", plugin.messages().state(plugin.pvp().isEnabled()),
                        "random", plugin.messages().state(plugin.pvp().isRandomEnabled()),
                        "timer", plugin.pvp().timerText());
            }
            case "activar", "desactivar" -> {
                if (!CommandUtil.has(sender, "extreme.pvp")) return deny(sender);
                boolean on = sub.equals("activar");
                if (!plugin.pvp().set(on, true)) plugin.messages().send(sender, on ? "pvp-already-on" : "pvp-already-off");
            }
            case "aleatorio" -> {
                if (!CommandUtil.has(sender, "extreme.pvp.random")) return deny(sender);
                Boolean on = args.length > 1 ? CommandUtil.parseBool(args[1]) : null;
                if (on == null) {
                    plugin.messages().send(sender, "usage-pvp-random");
                    return true;
                }
                plugin.pvp().setRandom(on);
                plugin.messages().send(sender, on ? "pvp-random-enabled" : "pvp-random-disabled");
            }
            default -> plugin.messages().send(sender, "usage-pvp");
        }
        return true;
    }

    private boolean deny(CommandSender sender) {
        plugin.messages().send(sender, "no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return CommandUtil.filter(List.of("activar", "desactivar", "estado", "aleatorio"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("aleatorio")) return CommandUtil.filter(List.of("activar", "desactivar"), args[1]);
        return List.of();
    }
}
