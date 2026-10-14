package com.extreme.commands;

import com.extreme.ExtremePlugin;
import com.extreme.gui.MainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /admin  -> abre el panel gráfico. */
public class AdminCommand implements CommandExecutor {

    private final ExtremePlugin plugin;

    public AdminCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!p.hasPermission("extreme.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        new MainMenu(plugin).open(p);
        return true;
    }
}
