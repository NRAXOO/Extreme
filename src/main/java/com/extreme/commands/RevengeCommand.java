package com.extreme.commands;

import com.extreme.ExtremePlugin;
import com.extreme.revenge.RevengeManager;
import com.extreme.utils.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /venganza -> tu estado; admin: /venganza lista | /venganza cancelar <jugador> */
public class RevengeCommand implements CommandExecutor, TabCompleter {

    private final ExtremePlugin plugin;

    public RevengeCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("lista")) {
            if (!CommandUtil.has(sender, "extreme.revenge.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.messages().send(sender, "revenge-list-header", "count", plugin.revenge().activeCount());
            for (RevengeManager.Revenge r : plugin.revenge().all()) {
                var v = plugin.getServer().getOfflinePlayer(r.victim());
                plugin.messages().send(sender, "revenge-list-entry", "victim", v.getName() == null ? "?" : v.getName(),
                        "killer", r.killerName(), "time", TimeUtil.format(Math.max(0, r.expires() - System.currentTimeMillis())));
            }
            return true;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("cancelar")) {
            if (!CommandUtil.has(sender, "extreme.revenge.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            Player p = plugin.getServer().getPlayerExact(args[1]);
            boolean ok = p != null && plugin.revenge().cancel(p.getUniqueId());
            plugin.messages().send(sender, ok ? "revenge-cancelled" : "revenge-none-other", "player", args[1]);
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        RevengeManager.Revenge r = plugin.revenge().get(p.getUniqueId());
        if (r == null) {
            plugin.messages().send(p, "revenge-none");
        } else {
            plugin.messages().send(p, "revenge-status", "killer", r.killerName(), "time", TimeUtil.format(plugin.revenge().remainingMs(p.getUniqueId())),
                    "lives", Math.max(1, plugin.configs().get("revenge.yml").getInt("revenge.steal-lives", 1)));
        }
        if (plugin.revenge().hunters(p.getUniqueId()) > 0) {
            plugin.messages().send(p, "revenge-hunted", "count", plugin.revenge().hunters(p.getUniqueId()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && CommandUtil.has(sender, "extreme.revenge.admin")) return CommandUtil.filter(List.of("lista", "cancelar"), args[0]);
        return List.of();
    }
}
