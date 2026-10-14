package com.extreme.difficulty;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Avisa del día y la dificultad al entrar (configurable en difficulty.yml: notify-on-join). */
public class DifficultyListener implements Listener {

    private final ExtremePlugin plugin;

    public DifficultyListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.configs().get("difficulty.yml").getBoolean("difficulty.notify-on-join", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!e.getPlayer().isOnline()) return;
            plugin.messages().send(e.getPlayer(), plugin.difficulty().isEnabled() ? "join-difficulty-on" : "join-difficulty-off",
                    "difficulty", plugin.difficulty().getLabel());
        }, 40L);
    }
}
