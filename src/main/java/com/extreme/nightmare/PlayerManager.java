package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supervivencia más dura (regeneración, comida, durabilidad, escudos, vida baja, caídas, XP) y
 * nivel de AMENAZA individual por jugador (decae solo con el tiempo, sin tareas).
 */
public class PlayerManager implements Listener {

    private record Threat(double value, long time) {}

    private final ExtremePlugin plugin;
    private final Map<UUID, Threat> threat = new HashMap<>();
    private final Map<UUID, Long> lowHealthCd = new HashMap<>();

    public PlayerManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    private boolean on(Player p) {
        return plugin.modules().on("nm_player") && plugin.nightmare().worldEnabled(p.getWorld()) && cfg().getBoolean("player.enabled", true);
    }

    // ------------------------------------------------------------------
    //  Amenaza individual
    // ------------------------------------------------------------------

    public double getThreat(Player p) {
        if (!plugin.modules().on("nm_threat")) return 0;
        Threat t = threat.get(p.getUniqueId());
        if (t == null) return 0;
        double halfLife = Math.max(1, cfg().getDouble("threat.half-life-minutes", 10)) * 60_000.0;
        return t.value() * Math.pow(0.5, (System.currentTimeMillis() - t.time()) / halfLife);
    }

    public void addThreat(Player p, double amount) {
        if (!plugin.modules().on("nm_threat")) return;
        double v = Math.min(cfg().getDouble("threat.max", 100), getThreat(p) + amount);
        threat.put(p.getUniqueId(), new Threat(v, System.currentTimeMillis()));
    }

    /** Multiplicador de spawn/eventos según la amenaza del jugador. */
    public double threatMult(Player p) {
        double bonus = Math.min(cfg().getDouble("threat.max-bonus", 1.0), getThreat(p) * cfg().getDouble("threat.bonus-per-point", 0.01));
        return 1.0 + bonus;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lowHealthCd.remove(e.getPlayer().getUniqueId());
        if (threat.size() > 200) threat.values().removeIf(t -> System.currentTimeMillis() - t.time() > 3_600_000L);
    }

    // ------------------------------------------------------------------
    //  Regeneración y comida
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !on(p)) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) {
            e.setAmount(e.getAmount() * cfg().getDouble("player.regen-multiplier", 0.5));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || !on(p)) return;
        YamlConfiguration c = cfg();
        int old = p.getFoodLevel();
        int now = e.getFoodLevel();
        if (now > old) {
            int gain = (int) Math.ceil((now - old) * c.getDouble("player.food-effectiveness", 0.6));
            e.setFoodLevel(Math.min(20, old + Math.max(0, gain)));
        }
        if (e.getFoodLevel() <= c.getInt("player.hunger-threshold", 6) && e.getFoodLevel() < old) {
            for (Effects.Spec s : Effects.parseSpecs(c.getStringList("player.hunger-effects"))) {
                if (ThreadLocalRandom.current().nextDouble() < s.chance()) p.addPotionEffect(s.create());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!on(p)) return;
        float before = p.getSaturation();
        double mult = cfg().getDouble("player.saturation-multiplier", 0.5);
        Bukkit.getScheduler().runTask(plugin, () -> {
            float after = p.getSaturation();
            if (after > before) p.setSaturation((float) (before + (after - before) * mult));
        });
    }

    // ------------------------------------------------------------------
    //  Daño: hambre, vida baja, caídas, escudos
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !on(p)) return;
        YamlConfiguration c = cfg();
        if (e.getCause() == EntityDamageEvent.DamageCause.STARVATION) {
            e.setDamage(e.getDamage() * c.getDouble("player.starvation-multiplier", 2.5));
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getDamage() >= c.getDouble("player.fall-effect-min-damage", 4.0)) {
            for (Effects.Spec s : Effects.parseSpecs(c.getStringList("player.fall-effects"))) {
                if (ThreadLocalRandom.current().nextDouble() < s.chance()) p.addPotionEffect(s.create());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageAfter(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !on(p)) return;
        YamlConfiguration c = cfg();
        double remaining = p.getHealth() - e.getFinalDamage();
        if (remaining <= 0 || remaining > c.getDouble("player.low-health-threshold", 6.0)) return;
        long now = System.currentTimeMillis();
        Long next = lowHealthCd.get(p.getUniqueId());
        if (next != null && now < next) return;
        lowHealthCd.put(p.getUniqueId(), now + c.getLong("player.low-health-cooldown", 20) * 1000L);
        for (Effects.Spec s : Effects.parseSpecs(c.getStringList("player.low-health-effects"))) {
            if (ThreadLocalRandom.current().nextDouble() < s.chance()) p.addPotionEffect(s.create());
        }
        Fx.actionBar(p, "&4Estás muy débil...");
    }

    /** Los escudos pueden romperse con golpes fuertes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShield(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !on(p) || !p.isBlocking()) return;
        YamlConfiguration c = cfg();
        if (e.getDamage() < c.getDouble("player.shield-break-min-damage", 5.0)
                || ThreadLocalRandom.current().nextDouble() >= c.getDouble("player.shield-break-chance", 0.2)) return;
        ItemStack shield = p.getInventory().getItemInMainHand().getType() == Material.SHIELD ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (shield.getType() != Material.SHIELD) return;
        p.setCooldown(Material.SHIELD, c.getInt("player.shield-disable-ticks", 100));
        p.clearActiveItem();
        ItemMeta meta = shield.getItemMeta();
        if (meta instanceof Damageable d) {
            d.setDamage(d.getDamage() + c.getInt("player.shield-extra-damage", 80));
            shield.setItemMeta(meta);
            if (d.getDamage() >= shield.getType().getMaxDurability()) shield.setAmount(0);
        }
        Fx.sound(plugin, p.getLocation(), "ITEM_SHIELD_BREAK", 1f, 0.8f);
        Fx.actionBar(p, "&c¡Tu escudo ha cedido!");
    }

    // ------------------------------------------------------------------
    //  Durabilidad
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        Player p = e.getPlayer();
        if (!on(p)) return;
        YamlConfiguration c = cfg();
        String n = e.getItem().getType().name().toLowerCase(Locale.ROOT);
        double mult = 1.0;
        if (n.endsWith("_helmet") || n.endsWith("_chestplate") || n.endsWith("_leggings") || n.endsWith("_boots") || n.equals("elytra")) {
            mult = c.getDouble("player.armor-durability-multiplier", 2.0);
        } else if (n.endsWith("_sword") || n.endsWith("_axe") || n.endsWith("_pickaxe") || n.endsWith("_shovel") || n.endsWith("_hoe")
                || n.equals("bow") || n.equals("crossbow") || n.equals("trident") || n.equals("shield")) {
            mult = c.getDouble("player.tool-durability-multiplier", 1.5);
        }
        if (mult != 1.0) {
            int extra = (int) Math.floor(e.getDamage() * (mult - 1.0));
            if (ThreadLocalRandom.current().nextDouble() < (e.getDamage() * (mult - 1.0)) - extra) extra++;
            e.setDamage(e.getDamage() + extra);
        }
    }

    // ------------------------------------------------------------------
    //  Muerte
    // ------------------------------------------------------------------

    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!on(p)) return;
        double loss = cfg().getDouble("player.xp-loss-percent", 0.5);
        if (loss <= 0) return;
        int total = p.getTotalExperience();
        int kept = (int) (total * Math.max(0.0, 1.0 - loss));
        e.setKeepLevel(false);
        e.setNewTotalExp(kept);
        e.setDroppedExp(0);
        threat.remove(p.getUniqueId());
    }
}
