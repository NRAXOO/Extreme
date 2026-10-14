package com.extreme.pvp;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Aplica el estado del PVP a mundos que se cargan después del arranque. */
public class PVPListener implements Listener {

    private final ExtremePlugin plugin;

    public PVPListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.pvp().applyToWorld(e.getWorld()));
    }
}
