package com.extreme.commands;

import com.extreme.ExtremePlugin;
import com.extreme.modules.ModuleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /extreme [ayuda|recargar|modulos|modulo <clave> <activar|desactivar>] */
public class ExtremeCommand implements CommandExecutor, TabCompleter {

    private final ExtremePlugin plugin;

    public ExtremeCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "ayuda";
        switch (sub) {
            case "recargar" -> {
                if (!CommandUtil.has(sender, "extreme.reload")) return deny(sender);
                plugin.reportReload(sender, plugin.reloadAll());
            }
            case "modulos" -> {
                if (!CommandUtil.has(sender, "extreme.admin")) return deny(sender);
                plugin.messages().send(sender, "modules-header");
                for (ModuleManager.Feature f : plugin.modules().all()) {
                    if (f.locked) continue;
                    plugin.messages().send(sender, "module-line", "key", f.key,
                            "name", plugin.messages().str("modules." + f.key + ".name"),
                            "state", plugin.messages().state(plugin.modules().on(f.key)));
                }
            }
            case "modulo" -> {
                if (!CommandUtil.has(sender, "extreme.admin")) return deny(sender);
                ModuleManager.Feature f = args.length > 1 ? plugin.modules().get(args[1].toLowerCase(Locale.ROOT)) : null;
                Boolean on = args.length > 2 ? CommandUtil.parseBool(args[2]) : null;
                if (f == null || f.locked) {
                    plugin.messages().send(sender, "module-unknown", "key", args.length > 1 ? args[1] : "?");
                } else if (on == null) {
                    plugin.messages().send(sender, "usage-module");
                } else {
                    plugin.modules().set(f.key, on);
                    plugin.messages().send(sender, "module-set", "name", plugin.messages().str("modules." + f.key + ".name"),
                            "state", plugin.messages().state(on));
                }
            }
            default -> plugin.messages().send(sender, "help", "version", plugin.getPluginMeta().getVersion());
        }
        return true;
    }

    private boolean deny(CommandSender sender) {
        plugin.messages().send(sender, "no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return CommandUtil.filter(List.of("ayuda", "recargar", "modulos", "modulo"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("modulo")) {
            List<String> keys = new ArrayList<>();
            for (ModuleManager.Feature f : plugin.modules().all()) if (!f.locked) keys.add(f.key);
            return CommandUtil.filter(keys, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("modulo")) return CommandUtil.filter(List.of("activar", "desactivar"), args[2]);
        return List.of();
    }
}
