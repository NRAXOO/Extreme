package com.extreme.commands;

import com.extreme.ExtremePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/** /dia  y  /dia fijar <día> */
public class DayCommand implements CommandExecutor, TabCompleter {

    private final ExtremePlugin plugin;

    public DayCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!CommandUtil.has(sender, "extreme.day")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.messages().send(sender, "day-status", "day", plugin.days().getDay(),
                    "max_day", plugin.days().getMaxDays(), "difficulty", plugin.difficulty().getLabel());
            return true;
        }
        if (args[0].equalsIgnoreCase("fijar")) {
            if (!CommandUtil.has(sender, "extreme.day.set")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            Integer d = args.length > 1 ? CommandUtil.parseInt(args[1]) : null;
            if (d == null || d < 1 || d > plugin.days().getMaxDays()) {
                plugin.messages().send(sender, "day-invalid", "max_day", plugin.days().getMaxDays());
                return true;
            }
            plugin.days().setDay(d, true);
            plugin.messages().send(sender, "day-set", "day", plugin.days().getDay());
            return true;
        }
        plugin.messages().send(sender, "usage-day");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return CommandUtil.filter(List.of("fijar"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("fijar")) {
            List<String> days = new ArrayList<>();
            for (int i = 1; i <= plugin.days().getMaxDays(); i++) days.add(String.valueOf(i));
            return CommandUtil.filter(days, args[1]);
        }
        return List.of();
    }
}
