package com.extreme.commands;

import com.extreme.ExtremePlugin;
import com.extreme.lives.LivesManager;
import com.extreme.lives.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /vidas, /vidas <jugador>, /vidas fijar|dar|quitar|reiniciar <jugador> [cantidad], /vidas lista */
public class LivesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("fijar", "dar", "quitar", "reiniciar", "lista");
    private final ExtremePlugin plugin;

    public LivesCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        LivesManager lives = plugin.lives();

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (!CommandUtil.has(sender, "extreme.lives.view")) return deny(sender);
            showLives(sender, lives.getOrCreate(p), "lives-self");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lista" -> {
                if (!CommandUtil.has(sender, "extreme.lives")) return deny(sender);
                list(sender);
            }
            case "fijar", "dar", "quitar", "reiniciar" -> {
                if (!CommandUtil.has(sender, "extreme.lives.modify")) return deny(sender);
                modify(sender, switch (sub) {
                    case "fijar" -> "set";
                    case "dar" -> "add";
                    case "quitar" -> "remove";
                    default -> "reset";
                }, args);
            }
            default -> {
                // /lives <jugador>
                if (!CommandUtil.has(sender, "extreme.lives")) return deny(sender);
                PlayerData d = lives.find(args[0]);
                if (d == null) {
                    plugin.messages().send(sender, "player-not-found", "player", args[0]);
                    return true;
                }
                showLives(sender, d, "lives-other");
            }
        }
        return true;
    }

    private void showLives(CommandSender to, PlayerData d, String key) {
        int max = plugin.lives().getMaxLives();
        plugin.messages().send(to, key, "player", d.name, "lives", d.lives, "max_lives", max,
                "hearts", "❤".repeat(Math.max(0, Math.min(d.lives, 30))));
        if (!plugin.lives().isEnabled()) plugin.messages().send(to, "lives-disabled-notice");
    }

    private void modify(CommandSender sender, String sub, String[] args) {
        LivesManager lives = plugin.lives();
        boolean needsAmount = !sub.equals("reset");
        if (args.length < (needsAmount ? 3 : 2)) {
            plugin.messages().send(sender, "usage-lives");
            return;
        }
        PlayerData d = lives.find(args[1]);
        if (d == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        int amount = 0;
        if (needsAmount) {
            Integer n = CommandUtil.parseInt(args[2]);
            if (n == null || n < 0) {
                plugin.messages().send(sender, "invalid-number", "value", args[2]);
                return;
            }
            amount = n;
        }
        int before = d.lives;
        int result = switch (sub) {
            case "set" -> lives.setLives(d, amount);
            case "add" -> lives.addLives(d, amount);
            case "remove" -> lives.addLives(d, -amount);
            default -> lives.resetLives(d);
        };
        plugin.messages().send(sender, "lives-" + sub, "player", d.name, "lives", result, "amount", amount,
                "max_lives", lives.getMaxLives());
        if (sub.equals("set") && amount > lives.getMaxLives()) {
            plugin.messages().send(sender, "lives-clamped", "max_lives", lives.getMaxLives());
        }
        Player online = Bukkit.getPlayer(d.id);
        if (online != null && result != before && !sender.equals(online)) {
            plugin.messages().send(online, "lives-changed-notice", "lives", result, "max_lives", lives.getMaxLives());
        }
        lives.saveIfDirty();
    }

    private void list(CommandSender sender) {
        List<PlayerData> all = plugin.lives().allByLives();
        int limit = Math.max(1, plugin.configs().get("lives.yml").getInt("lives.list-limit", 50));
        plugin.messages().send(sender, "lives-list-header", "count", all.size(), "max_lives", plugin.lives().getMaxLives());
        int shown = 0;
        for (PlayerData d : all) {
            if (shown++ >= limit) {
                plugin.messages().send(sender, "lives-list-more", "count", all.size() - limit);
                break;
            }
            plugin.messages().send(sender, "lives-list-entry", "player", d.name, "lives", d.lives,
                    "max_lives", plugin.lives().getMaxLives(),
                    "hearts", "❤".repeat(Math.max(0, Math.min(d.lives, 30))));
        }
    }

    private boolean deny(CommandSender sender) {
        plugin.messages().send(sender, "no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> names = new ArrayList<>();
        if (args.length == 1) {
            names.addAll(SUBS);
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return CommandUtil.filter(names, args[0]);
        }
        if (args.length == 2 && List.of("fijar", "dar", "quitar", "reiniciar").contains(args[0].toLowerCase(Locale.ROOT))) {
            plugin.lives().all().forEach(d -> names.add(d.name));
            return CommandUtil.filter(names, args[1]);
        }
        if (args.length == 3 && List.of("fijar", "dar", "quitar").contains(args[0].toLowerCase(Locale.ROOT))) {
            return CommandUtil.filter(List.of("1", "3", "5", "10"), args[2]);
        }
        return List.of();
    }
}
