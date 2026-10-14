package com.extreme.lives;

import com.extreme.ExtremePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LivesListener implements Listener {

    private final ExtremePlugin plugin;

    public LivesListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        plugin.lives().handleDeath(e);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.lives().handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.lives().handleQuit(e.getPlayer());
    }
}
