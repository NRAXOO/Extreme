package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Combate: multiplicación, veneno, golems devastadores, esqueletos aimbot, creepers especiales y recompensas. */
public class NightmareListener implements Listener {

    private record Sample(Vector pos, long time) {}

    private final ExtremePlugin plugin;
    private final Map<UUID, Sample> samples = new HashMap<>();

    public NightmareListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    private boolean roll(double chance) { return ThreadLocalRandom.current().nextDouble() < chance; }

    // ------------------------------------------------------------------
    //  Spawn
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        plugin.nmMobs().apply(e.getEntity(), e.getSpawnReason());
    }

    // ------------------------------------------------------------------
    //  Daño
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (!plugin.nightmare().worldEnabled(victim.getWorld())) return;

        // Jugador golpea a un mob
        if (damager instanceof Player pl && victim instanceof LivingEntity lv && !(victim instanceof Player)) {
            plugin.multiplication().tryMultiply(lv, pl, "DAMAGE");
            if (victim instanceof Mob m && (MobTags.getType(m) != null || MobTags.isElite(m))) plugin.ai().noteAttacker(m, pl);
            if (victim instanceof IronGolem g && plugin.modules().on("nm_golems")) angerGolem(g, pl);
            return;
        }

        // Mob golpea a un jugador
        if (victim instanceof Player target && damager instanceof LivingEntity mob && !(mob instanceof Player)) {
            mobHitsPlayer(e, mob, target);
        }
    }

    private void mobHitsPlayer(EntityDamageByEntityEvent e, LivingEntity mob, Player target) {
        YamlConfiguration c = cfg();
        String type = MobTags.getType(mob);

        plugin.multiplication().tryMultiply(mob, target, "HIT");
        if ("zombie_summoner".equals(type) && mob instanceof Mob m) plugin.ai().summon(m, target);

        // Veneno prolongado (hasta poison.max-duration segundos)
        boolean spider = mob.getType() == EntityType.SPIDER || mob.getType() == EntityType.CAVE_SPIDER;
        if ((plugin.modules().on("nm_spiders") && (spider || "venom_spider".equals(type))) || MobTags.getBoss(mob) != null) {
            boolean special = "venom_spider".equals(type) || MobTags.getBoss(mob) != null;
            double chance = special ? c.getDouble("poison.special-chance", 0.9) : c.getDouble("spiders.poison-chance", 0.35);
            if (roll(chance)) {
                int sec = special ? c.getInt("poison.special-duration", 180) : c.getInt("spiders.poison-duration", 30);
                poison(target, sec);
            }
        }

        // Golems: ataque devastador tras ser golpeados
        if (mob instanceof IronGolem g && plugin.modules().on("nm_golems")) {
            long now = System.currentTimeMillis();
            boolean angry = now < MobTags.getLong(g, MobTags.ANGRY_UNTIL);
            if (angry && now >= MobTags.getLong(g, MobTags.NEXT_ABILITY) && roll(c.getDouble("golems.devastating-chance", 0.7))) {
                e.setDamage(c.getDouble("golems.devastating-damage", 1000.0));
                MobTags.setLong(g, MobTags.NEXT_ABILITY, now + (long) (c.getDouble("golems.devastating-cooldown", 8) * 1000));
                Fx.particle(target.getLocation().add(0, 1, 0), "EXPLOSION_EMITTER", 1, 0, 0);
                Fx.sound(plugin, target.getLocation(), "ENTITY_IRON_GOLEM_ATTACK", 1.5f, 0.5f);
            }
        }
    }

    /** Aplica veneno limitado por poison.max-duration. */
    public void poison(Player p, int seconds) {
        int max = cfg().getInt("poison.max-duration", 180);
        int ticks = Math.max(20, Math.min(seconds, max) * 20);
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, cfg().getInt("poison.amplifier", 0), false, true, true));
    }

    private void angerGolem(IronGolem g, Player attacker) {
        YamlConfiguration c = cfg();
        if (!c.getBoolean("golems.enraged-on-hit", true)) return;
        MobTags.setLong(g, MobTags.ANGRY_UNTIL, System.currentTimeMillis() + (long) (c.getDouble("golems.angry-seconds", 40) * 1000));
        g.setTarget(attacker);
        g.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 40, 1, true, false, false));
        g.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 40, 2, true, false, false));
        Fx.particle(g.getLocation().add(0, 2, 0), "ANGRY_VILLAGER", 8, 0.5, 0.0);
    }

    // ------------------------------------------------------------------
    //  Esqueletos: precisión extrema, flechas especiales, ráfagas
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof AbstractSkeleton sk) || !plugin.nightmare().worldEnabled(sk.getWorld()) || !plugin.modules().on("nm_skeletons")) return;
        if (!(sk.getTarget() instanceof Player p)) return;
        YamlConfiguration c = cfg();
        Entity proj = e.getProjectile();

        boolean sniper = MobTags.isType(sk, "skeleton_sniper") || "skeleton_sniper".equals(MobTags.getBoss(sk));
        double accuracy = sniper ? c.getDouble("skeletons.sniper-accuracy", 0.995) : c.getDouble("skeletons.accuracy", 0.92);
        proj.setVelocity(aim(sk, p, proj.getVelocity().length(), accuracy));

        String kind = chooseArrow(sniper);
        if (kind != null) markArrow(proj, kind);

        double burst = sniper ? c.getDouble("skeletons.sniper-burst-chance", 0.5) : c.getDouble("skeletons.burst-chance", 0.25);
        if (roll(burst)) {
            double speed = proj.getVelocity().length();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!sk.isValid() || !p.isOnline() || p.isDead() || sk.getWorld() != p.getWorld()) return;
                Arrow a = sk.launchProjectile(Arrow.class, aim(sk, p, speed, accuracy));
                String k = chooseArrow(sniper);
                if (k != null) markArrow(a, k);
            }, c.getInt("skeletons.burst-delay-ticks", 6));
        }
    }

    private Vector aim(AbstractSkeleton sk, Player p, double speed, double accuracy) {
        Location from = sk.getEyeLocation();
        Location to = p.getLocation().add(0, p.getHeight() * 0.65, 0);

        // Velocidad estimada del jugador (blocks/tick) a partir de muestras anteriores
        long now = System.currentTimeMillis();
        Sample prev = samples.get(p.getUniqueId());
        Vector vel = new Vector();
        if (prev != null && now - prev.time() > 50 && now - prev.time() < 1500) {
            double ticks = (now - prev.time()) / 50.0;
            vel = to.toVector().subtract(prev.pos()).multiply(1.0 / ticks);
        }
        samples.put(p.getUniqueId(), new Sample(to.toVector(), now));
        if (samples.size() > 200) samples.values().removeIf(s -> now - s.time() > 10_000L);

        double s = Math.max(1.0, speed);
        double dist = from.distance(to);
        double t = dist / s;
        Vector predicted = to.toVector().add(vel.multiply(t * accuracy));
        predicted.setY(predicted.getY() + 0.5 * 0.05 * t * t * 2.0); // compensación de caída
        Vector dir = predicted.subtract(from.toVector()).normalize();
        double spread = (1.0 - accuracy) * 0.25;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        dir.add(new Vector(r.nextDouble(-spread, spread), r.nextDouble(-spread, spread), r.nextDouble(-spread, spread))).normalize();
        return dir.multiply(s);
    }

    private String chooseArrow(boolean sniper) {
        YamlConfiguration c = cfg();
        double r = ThreadLocalRandom.current().nextDouble();
        double bonus = sniper ? 1.5 : 1.0;
        double acc = 0;
        for (String k : new String[]{"poison", "slowness", "weakness", "explosive"}) {
            acc += c.getDouble("skeletons.arrows." + k, 0.05) * bonus;
            if (r < acc) return k;
        }
        return null;
    }

    private void markArrow(Entity proj, String kind) {
        YamlConfiguration c = cfg();
        MobTags.setArrow(proj, kind);
        if (proj instanceof Arrow a) {
            switch (kind) {
                case "poison" -> a.addCustomEffect(new PotionEffect(PotionEffectType.POISON,
                        Math.min(c.getInt("poison.max-duration", 180), c.getInt("skeletons.arrow-poison-duration", 20)) * 20, 0), true);
                case "slowness" -> a.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 8, 1), true);
                case "weakness" -> a.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 10, 1), true);
                default -> { }
            }
        }
        if (kind.equals("explosive")) Fx.particle(proj.getLocation(), "FLAME", 3, 0.05, 0.0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!"explosive".equals(MobTags.getArrow(pr))) return;
        Location loc = pr.getLocation();
        pr.remove();
        if (!plugin.perf().tryExplosion()) return;
        YamlConfiguration c = cfg();
        loc.getWorld().createExplosion(loc, (float) c.getDouble("skeletons.arrow-explosion-power", 1.8), false,
                c.getBoolean("skeletons.arrow-break-blocks", false), pr);
    }

    // ------------------------------------------------------------------
    //  Creepers
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent e) {
        Entity src = e.getEntity();
        if (!plugin.nightmare().worldEnabled(src.getWorld())) return;
        YamlConfiguration c = cfg();

        // Bolas de fuego del Ghast Demoníaco: explosión personalizada
        if (src instanceof Fireball fb && "demonic_fireball".equals(MobTags.getArrow(fb)) && plugin.modules().on("nm_ghast")) {
            if (!plugin.perf().tryExplosion()) {
                e.setCancelled(true);
                fb.remove();
                return;
            }
            e.setRadius((float) c.getDouble("ghast.explosion-power", 4.0));
            e.setFire(c.getBoolean("ghast.incendiary", true));
            return;
        }

        if (!(src instanceof Creeper cr) || !plugin.modules().on("nm_creepers")) return;
        String type = MobTags.getType(cr);
        String boss = MobTags.getBoss(cr);

        // CREEPER KING: si explota, la explosión es GIGANTESCA (no se limita por presupuesto)
        if ("creeper_king".equals(boss)) {
            e.setRadius((float) c.getDouble("bosses.creeper_king.explosion-radius", 24));
            e.setFire(c.getBoolean("bosses.creeper_king.explosion-fire", false));
            Fx.sound(plugin, cr.getLocation(), "ENTITY_GENERIC_EXPLODE", 4f, 0.5f);
            plugin.bosses().onExploded(cr);
            return;
        }
        if (boss != null) return;

        if (!plugin.perf().tryExplosion()) {
            e.setCancelled(true);
            cr.remove(); // control de explosiones: se descarta el creeper en vez de explotar
            return;
        }

        // Explosión personalizable de TODOS los creepers
        double base = c.getDouble("creepers.explosion-radius", 3.0);
        if ("creeper_big".equals(type)) base = c.getDouble("creepers.big-radius", 7);
        else if ("creeper_giant".equals(type)) base = c.getDouble("creepers.giant-radius", 6);
        e.setRadius((float) (base * (cr.isPowered() ? 2.0 : 1.0)));
        if ("creeper_fire".equals(type) || c.getBoolean("creepers.explosion-fire", false)) e.setFire(true);

        if ("creeper_brood".equals(type)) {
            int n = c.getInt("creepers.brood-amount", 2);
            Location base2 = cr.getLocation();
            UUID owner = null;
            PerformanceManager.Tracked info = plugin.perf().info(cr.getUniqueId());
            if (info != null) owner = info.owner();
            UUID fOwner = owner;
            int gen = MobTags.getGen(cr);
            if (gen < c.getInt("creepers.brood-max-generations", 2)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < n; i++) {
                        LivingEntity child = plugin.spawns().spawnAt(EntityType.CREEPER, plugin.spawns().spotNear(base2, 4), fOwner, null);
                        if (child == null) break;
                        MobTags.setGen(child, gen + 1);
                        MobTags.setType(child, "creeper_brood");
                    }
                });
            }
        }
    }

    /** Decide si las explosiones de creepers / ghast demoníaco destruyen bloques (configurable). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Entity src = e.getEntity();
        YamlConfiguration c = cfg();
        if (src instanceof Fireball fb && "demonic_fireball".equals(MobTags.getArrow(fb))) {
            if (!c.getBoolean("ghast.break-blocks", true)) e.blockList().clear();
            return;
        }
        if (src instanceof Creeper cr && plugin.modules().on("nm_creepers") && plugin.nightmare().worldEnabled(cr.getWorld())) {
            boolean breaks = MobTags.getBoss(cr) != null
                    ? c.getBoolean("bosses.creeper_king.explosion-break-blocks", true)
                    : c.getBoolean("creepers.explosion-break-blocks", true);
            if (!breaks) e.blockList().clear();
        }
    }

    // ------------------------------------------------------------------
    //  Ghast Demoníaco (Nether): bolas de fuego con explosión propia
    // ------------------------------------------------------------------

    private boolean volley;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (volley || !(e.getEntity() instanceof Fireball fb) || !(fb.getShooter() instanceof Ghast g)) return;
        if (!"ghast_demoniaco".equals(MobTags.getType(g)) || !plugin.modules().on("nm_ghast")) return;
        YamlConfiguration c = cfg();
        mark(fb, c);
        int extra = Math.max(0, Math.min(6, c.getInt("ghast.extra-fireballs", 2)));
        double spread = c.getDouble("ghast.spread", 0.25);
        volley = true;
        try {
            for (int i = 0; i < extra; i++) {
                Vector dir = fb.getDirection().clone().rotateAroundY((i % 2 == 0 ? 1 : -1) * spread * (i / 2 + 1));
                LargeFireball ex = g.launchProjectile(LargeFireball.class, dir);
                mark(ex, c);
            }
        } finally {
            volley = false;
        }
        Fx.sound(plugin, g.getLocation(), "ENTITY_GHAST_WARN", 2f, 0.5f);
    }

    private void mark(Fireball fb, YamlConfiguration c) {
        MobTags.setArrow(fb, "demonic_fireball");
        fb.setYield((float) c.getDouble("ghast.explosion-power", 4.0));
        fb.setIsIncendiary(c.getBoolean("ghast.incendiary", true));
    }

    // ------------------------------------------------------------------
    //  Muertes
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        plugin.perf().untrack(le.getUniqueId());
        if (le instanceof Player) return;
        YamlConfiguration c = cfg();
        Player killer = le.getKiller();

        boolean elite = MobTags.isElite(le);
        String type = MobTags.getType(le);
        if (elite || type != null && (type.equals("skeleton_sniper") || type.equals("zombie_summoner") || type.equals("golem_elite"))) {
            Drops.roll(Drops.parse(c.getStringList(elite ? "elite.drops" : "special-drops")), le.getLocation(),
                    1.0 + plugin.nightmare().getLevel() * 0.02);
            e.setDroppedExp((int) (e.getDroppedExp() * c.getDouble("elite.xp-multiplier", 3.0)));
        }

        plugin.bosses().onDeath(le, killer);
        if (killer != null) {
            plugin.vengeance().onKill(killer, le);
            plugin.playerManager().addThreat(killer, elite ? 1.5 : 0.4);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        plugin.vengeance().onPlayerDeath(e.getEntity());
        plugin.playerManager().onPlayerDeath(e);
    }
}
