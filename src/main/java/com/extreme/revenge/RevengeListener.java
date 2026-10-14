package com.extreme.revenge;

import com.extreme.ExtremePlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class RevengeListener implements Listener {

    private final ExtremePlugin plugin;

    public RevengeListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * La venganza SOLO existe entre jugadores: el golpe que mató a la víctima debe venir directamente de otro
     * jugador (cuerpo a cuerpo o con su proyectil). Mobs, lava, caídas, explosiones de mobs, mascotas, etc. NO cuentan.
     */
    static Player directPlayerKiller(Player victim) {
        EntityDamageEvent last = victim.getLastDamageCause();
        if (!(last instanceof EntityDamageByEntityEvent hit)) return null;
        Entity damager = hit.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (damager instanceof Player p && !p.equals(victim) && p.equals(victim.getKiller())) return p;
        return null;
    }

    /** MONITOR: se ejecuta después de que el sistema de vidas haya procesado la muerte. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = directPlayerKiller(victim);
        if (killer != null) plugin.revenge().onPlayerKill(victim, killer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        RevengeManager.Revenge r = plugin.revenge().get(p.getUniqueId());
        if (r != null) {
            plugin.messages().send(p, "revenge-reminder", "killer", r.killerName(),
                    "time", com.extreme.utils.TimeUtil.format(plugin.revenge().remainingMs(p.getUniqueId())));
        }
    }
}
