package com.extreme.mobs;

import com.extreme.ExtremePlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Solo reacciona a eventos (spawn y daño): no hay tareas que revisen mobs constantemente. */
public class MobListener implements Listener {

    private final ExtremePlugin plugin;

    public MobListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        plugin.mobs().applySpawn(e.getEntity(), e.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;

        // Mob -> jugador
        if (victim instanceof Player player && damager instanceof LivingEntity mob && !(mob instanceof Player)) {
            if (plugin.mobs().handles(mob) && plugin.mobs().worldAllowed(mob.getWorld())) {
                double mult = plugin.mobs().damageMultiplier(mob.getType());
                if (mult != 1.0) e.setDamage(e.getDamage() * mult);
                plugin.effects().onHit(mob, player);
            }
            plugin.spiders().trigger(mob, player);
            return;
        }

        // Jugador -> araña (también cuenta como combate)
        if (damager instanceof Player attacker && plugin.spiders().isCobwebSpider(victim)) {
            plugin.spiders().trigger(victim, attacker);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        plugin.spiders().forget(e.getEntity().getUniqueId());
    }
}
