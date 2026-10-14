package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import com.extreme.utils.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Mini-jefes, jefes mundiales y Wardens. Los jefes se identifican por PDC y se procesan en UNA tarea de 1 segundo. */
public class BossManager {

    private static class Boss {
        UUID id;
        String key;
        boolean world;
        BossBar bar;
        final Map<String, Long> cooldowns = new HashMap<>();
    }

    private final ExtremePlugin plugin;
    private final Map<UUID, Boss> bosses = new HashMap<>();
    private BukkitTask task;

    public BossManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Boss b : bosses.values()) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(b.bar);
        bosses.clear();
    }

    public int count() { return bosses.size(); }

    public boolean canSpawnMore() { return bosses.size() < plugin.perf().maxBosses(); }

    public Iterable<String> keys() {
        ConfigurationSection s = cfg().getConfigurationSection("bosses");
        List<String> out = new ArrayList<>();
        if (s != null) for (String k : s.getKeys(false)) if (s.isConfigurationSection(k)) out.add(k);
        return out;
    }

    // ------------------------------------------------------------------
    //  Creación
    // ------------------------------------------------------------------

    public LivingEntity spawn(String key, Location loc, boolean world) {
        YamlConfiguration c = cfg();
        ConfigurationSection s = c.getConfigurationSection("bosses." + key);
        if (s == null) return null;
        EntityType type;
        try {
            type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Entity ent = plugin.spawns().spawnRaw(type, loc, e -> {
            MobTags.setOurs(e);
            MobTags.setBoss(e, key);
            if (e instanceof Mob m) m.setRemoveWhenFarAway(false);
        });
        if (!(ent instanceof LivingEntity le)) return null;

        double lvl = 1.0 + c.getDouble("bosses.level-scaling", 0.05) * (plugin.nightmare().getLevel() - 1);
        double worldMult = world ? c.getDouble("world-boss.multiplier", 2.5) : 1.0;
        base(le, Attribute.MAX_HEALTH, Math.min(1024.0, s.getDouble("health", 300) * lvl * worldMult));
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) le.setHealth(hp.getValue());
        if (le.getAttribute(Attribute.ATTACK_DAMAGE) != null) base(le, Attribute.ATTACK_DAMAGE, s.getDouble("damage", 10) * (1 + (lvl - 1) * 0.5) * worldMult);
        AttributeInstance sp = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(sp.getBaseValue() * (1.0 + s.getDouble("speed-bonus", 0.2)));
        base(le, Attribute.FOLLOW_RANGE, 48);
        base(le, Attribute.SCALE, s.getDouble("scale", 1.0) * (world ? 1.25 : 1.0));
        base(le, Attribute.KNOCKBACK_RESISTANCE, 0.8);
        le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, world ? 2 : 1, true, false, false));

        String name = s.getString("name", key) + (world ? " &8[&4Jefe Mundial&8]" : "");
        le.customName(Text.color(name));
        le.setCustomNameVisible(true);
        if (le instanceof Creeper cr && key.equals("creeper_king")) {
            cr.setPowered(true);
            cr.setExplosionRadius(s.getInt("explosion-radius", 6));
        }
        if (le.getEquipment() != null && type == EntityType.SKELETON) {
            ItemStack bow = new ItemStack(Material.BOW);
            bow.addUnsafeEnchantment(Enchantment.POWER, 7);
            le.getEquipment().setItemInMainHand(bow);
            le.getEquipment().setItemInMainHandDropChance(0f);
        }

        Boss b = new Boss();
        b.id = le.getUniqueId();
        b.key = key;
        b.world = world;
        b.bar = BossBar.bossBar(Text.color(name), 1f, barColor(s.getString("bar-color", "RED")), BossBar.Overlay.NOTCHED_10);
        bosses.put(b.id, b);
        plugin.perf().track(le, null, "boss", "boss");
        ensureTask();
        Fx.sound(plugin, loc, "ENTITY_WITHER_SPAWN", 1.2f, 0.6f);
        Fx.particle(loc.clone().add(0, 1, 0), "EXPLOSION_EMITTER", 1, 0, 0);
        return le;
    }

    private void base(LivingEntity le, Attribute a, double v) {
        AttributeInstance inst = le.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private BossBar.Color barColor(String n) {
        try {
            return BossBar.Color.valueOf(n.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    /** Vuelve a registrar jefes cargados desde disco (tras reinicio o carga de chunks). */
    public void adopt(Entity e) {
        String key = MobTags.getBoss(e);
        if (key == null || !(e instanceof LivingEntity le) || bosses.containsKey(e.getUniqueId())) return;
        ConfigurationSection s = cfg().getConfigurationSection("bosses." + key);
        if (s == null) return;
        Boss b = new Boss();
        b.id = e.getUniqueId();
        b.key = key;
        String name = s.getString("name", key);
        b.bar = BossBar.bossBar(Text.color(name), 1f, barColor(s.getString("bar-color", "RED")), BossBar.Overlay.NOTCHED_10);
        bosses.put(b.id, b);
        plugin.perf().track(le, null, "boss", "boss");
        ensureTask();
    }

    public LivingEntity spawnRandomMini(Location loc) {
        List<String> pool = cfg().getStringList("bosses.mini-pool");
        if (pool.isEmpty() || !canSpawnMore() || !plugin.modules().on("nm_bosses")) return null;
        String key = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        LivingEntity le = spawn(key, loc, false);
        if (le != null) {
            for (Player p : le.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) < 80 * 80) {
                    Fx.chat(List.of(p), "&4⚠ &cUn mini-jefe ha aparecido cerca: " + cfg().getString("bosses." + key + ".name", key));
                    Fx.soundTo(plugin, p, "ENTITY_ELDER_GUARDIAN_CURSE", 1f, 0.8f);
                }
            }
        }
        return le;
    }

    /** Wardens en cuevas normales (sin Ancient City). Limitados por mundo. */
    public boolean trySpawnWarden(Location loc, boolean mutated) {
        YamlConfiguration c = cfg();
        World w = loc.getWorld();
        int max = c.getInt("wardens.max-per-world", 2);
        if (w.getEntitiesByClass(Warden.class).size() >= max) return false;
        if (mutated) {
            if (!canSpawnMore()) return false;
            return spawn("mutated_warden", loc, false) != null;
        }
        Entity e = plugin.spawns().spawnRaw(EntityType.WARDEN, loc, ent -> {
            MobTags.setOurs(ent);
            MobTags.setType(ent, "warden");
        });
        if (e instanceof LivingEntity le) {
            plugin.perf().track(le, null, "warden", "warden");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Jefe mundial: punto de aparición elegido + cuenta atrás con coordenadas
    // ------------------------------------------------------------------

    public record SpawnPoint(String name, String world, double x, double y, double z, double radius) {}

    private static class Pending {
        String key;
        Location loc;
        long start, end;
        BossBar bar;
        int lastReminder = Integer.MAX_VALUE;
        boolean stormSet;
        BukkitTask task;
    }

    private Pending pending;

    /** Puntos de aparición del jefe mundial (zonas del mundo elegidas por un administrador). */
    public List<SpawnPoint> points() {
        List<SpawnPoint> out = new ArrayList<>();
        for (Map<?, ?> m : cfg().getMapList("world-boss.spawn-points")) {
            try {
                out.add(new SpawnPoint(String.valueOf(m.get("name")), String.valueOf(m.get("world")),
                        ((Number) m.get("x")).doubleValue(), ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(), ((Number) m.get("radius")).doubleValue()));
            } catch (RuntimeException ignored) {
                // entrada inválida
            }
        }
        return out;
    }

    private void savePoints(List<SpawnPoint> list) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (SpawnPoint sp : list) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", sp.name());
            m.put("world", sp.world());
            m.put("x", sp.x());
            m.put("y", sp.y());
            m.put("z", sp.z());
            m.put("radius", sp.radius());
            raw.add(m);
        }
        plugin.configs().set("nightmare.yml", "world-boss.spawn-points", raw);
    }

    public void addPoint(SpawnPoint p) {
        List<SpawnPoint> list = points();
        list.removeIf(o -> o.name().equalsIgnoreCase(p.name()));
        list.add(p);
        savePoints(list);
    }

    public boolean removePoint(String name) {
        List<SpawnPoint> list = points();
        boolean r = list.removeIf(o -> o.name().equalsIgnoreCase(name));
        if (r) savePoints(list);
        return r;
    }

    public boolean isCountdownActive() { return pending != null; }

    public long countdownRemainingMs() { return pending == null ? 0L : Math.max(0L, pending.end - System.currentTimeMillis()); }

    public String countdownCoords() {
        if (pending == null) return "";
        return pending.loc.getBlockX() + ", " + pending.loc.getBlockY() + ", " + pending.loc.getBlockZ() + " (" + pending.loc.getWorld().getName() + ")";
    }

    public void cancelCountdown() {
        if (pending == null) return;
        if (pending.task != null) pending.task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(pending.bar);
        pending = null;
    }

    /** Milisegundos hasta el próximo jefe mundial PROGRAMADO por días (-1 si no hay). */
    public long nextScheduledMs() {
        YamlConfiguration c = cfg();
        if (!c.getBoolean("world-boss.enabled", true) || !plugin.modules().on("nm_world_boss")) return -1L;
        int raw = plugin.days().getRawDay();
        int first = c.getInt("world-boss.first-day", 7);
        int every = Math.max(1, c.getInt("world-boss.every-days", 7));
        int d = raw < first ? first : raw + ((every - ((raw - first) % every)) % every);
        if (d == raw && plugin.state().lastWorldBossDay == raw) d += every;
        long start = plugin.state().dayStart + (d - 1L) * plugin.days().getDayMs();
        return Math.max(0L, start - System.currentTimeMillis());
    }

    public void checkWorldBossSchedule(int rawDay) {
        YamlConfiguration c = cfg();
        if (!c.getBoolean("world-boss.enabled", true) || !plugin.modules().on("nm_world_boss") || !plugin.nightmare().isEnabled()) return;
        int first = c.getInt("world-boss.first-day", 7);
        int every = Math.max(1, c.getInt("world-boss.every-days", 7));
        if (rawDay < first || (rawDay - first) % every != 0 || plugin.state().lastWorldBossDay == rawDay) return;
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() == GameMode.SURVIVAL && plugin.nightmare().worldEnabled(p.getWorld())) players.add(p);
        if (players.isEmpty() || !canSpawnMore() || pending != null) return;
        plugin.state().lastWorldBossDay = rawDay;
        plugin.state().save();
        startWorldBoss(players.get(ThreadLocalRandom.current().nextInt(players.size())), null);
    }

    /** Elige DÓNDE aparecerá: un punto configurado (zona del mundo) o, si no hay, lejos de un jugador. */
    private Location chooseLocation(Player target) {
        YamlConfiguration c = cfg();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<SpawnPoint> valid = new ArrayList<>();
        for (SpawnPoint sp : points()) if (Bukkit.getWorld(sp.world()) != null) valid.add(sp);
        if (!valid.isEmpty() && c.getBoolean("world-boss.use-points", true)) {
            SpawnPoint sp = valid.get(r.nextInt(valid.size()));
            World w = Bukkit.getWorld(sp.world());
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(r.nextDouble()) * sp.radius();
            int x = (int) Math.floor(sp.x() + Math.cos(ang) * dist);
            int z = (int) Math.floor(sp.z() + Math.sin(ang) * dist);
            int y = c.getBoolean("world-boss.surface-only", true) ? w.getHighestBlockYAt(x, z) + 1 : (int) sp.y();
            return plugin.spawns().spotNear(new Location(w, x + 0.5, y, z + 0.5), 2);
        }
        if (target == null || !c.getBoolean("world-boss.random-fallback", true)) return null;
        Location loc = plugin.spawns().findLocation(target, 40, 80);
        return loc != null ? loc : plugin.spawns().spotNear(target.getLocation(), 30);
    }

    /**
     * Anuncia el jefe mundial con sus COORDENADAS y arranca una cuenta atrás (barra de jefe + avisos en el chat).
     * Al llegar a 0 aparece en el punto elegido.
     */
    public boolean startWorldBoss(Player target, String forcedKey) {
        YamlConfiguration c = cfg();
        if (pending != null || !canSpawnMore()) return false;
        Location loc = chooseLocation(target);
        if (loc == null) return false;
        List<String> pool = c.getStringList("world-boss.pool");
        String key = forcedKey != null ? forcedKey : (pool.isEmpty() ? "zombie_king" : pool.get(ThreadLocalRandom.current().nextInt(pool.size())));

        Pending p = new Pending();
        p.key = key;
        p.loc = loc;
        p.start = System.currentTimeMillis();
        p.end = p.start + Math.max(10, c.getInt("world-boss.announce-seconds", 300)) * 1000L;
        p.bar = BossBar.bossBar(Text.color("&4☠ Jefe Mundial"), 1f, barColor(c.getString("world-boss.bar-color", "PURPLE")), BossBar.Overlay.PROGRESS);
        pending = p;

        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        String coords = coordsText(loc);
        Fx.title(all, c.getString("world-boss.title", "&4&lALGO ANTIGUO HA DESPERTADO."),
                c.getBoolean("world-boss.show-coordinates", true) ? "&c" + coords : c.getString("world-boss.subtitle", "&cLa tierra tiembla..."));
        Fx.chat(all, c.getString("world-boss.chat", "&4ALGO ANTIGUO HA DESPERTADO.")
                + (c.getBoolean("world-boss.show-coordinates", true) ? " &cAparecerá en &f" + coords + " &cen &f" + com.extreme.utils.TimeUtil.format(p.end - p.start) + "&c." : ""));
        for (Player pl : all) {
            pl.showBossBar(p.bar);
            Fx.soundTo(plugin, pl, "ENTITY_WITHER_SPAWN", 0.8f, 0.5f);
        }
        p.task = Bukkit.getScheduler().runTaskTimer(plugin, this::countdownTick, 20L, 20L);
        return true;
    }

    private String coordsText(Location l) {
        return "X: " + l.getBlockX() + "  Y: " + l.getBlockY() + "  Z: " + l.getBlockZ() + " (" + l.getWorld().getName() + ")";
    }

    private void countdownTick() {
        Pending p = pending;
        if (p == null) return;
        YamlConfiguration c = cfg();
        long now = System.currentTimeMillis();
        long left = p.end - now;
        int secs = (int) Math.ceil(left / 1000.0);

        if (left <= 0) {
            for (Player pl : Bukkit.getOnlinePlayers()) pl.hideBossBar(p.bar);
            p.task.cancel();
            pending = null;
            p.loc.getChunk().load();
            LivingEntity boss = spawn(p.key, p.loc, true);
            if (boss != null) {
                Fx.chat(new ArrayList<>(Bukkit.getOnlinePlayers()), "&4&l☠ &c" + Text.plain(c.getString("bosses." + p.key + ".name", p.key))
                        + " &4&lha aparecido en &f" + coordsText(p.loc) + "&4&l.");
                for (Player pl : Bukkit.getOnlinePlayers()) Fx.soundTo(plugin, pl, "ENTITY_ENDER_DRAGON_GROWL", 1f, 0.5f);
            }
            return;
        }

        // Barra con contador y coordenadas (para todos los jugadores conectados)
        String timer = com.extreme.utils.TimeUtil.clock(left);
        p.bar.name(Text.color("&4☠ Jefe Mundial &7en &f" + timer
                + (c.getBoolean("world-boss.show-coordinates", true) ? " &8| &c" + coordsText(p.loc) : "")));
        p.bar.progress((float) Math.max(0.0, Math.min(1.0, left / (double) (p.end - p.start))));
        for (Player pl : Bukkit.getOnlinePlayers()) pl.showBossBar(p.bar);

        // Avisos en el chat
        if (c.getIntegerList("world-boss.reminders").contains(secs) && secs < p.lastReminder) {
            p.lastReminder = secs;
            Fx.chat(new ArrayList<>(Bukkit.getOnlinePlayers()), "&4☠ &cJefe mundial en &f" + (secs >= 60 ? (secs / 60) + " min" : secs + " s")
                    + (c.getBoolean("world-boss.show-coordinates", true) ? " &7- &f" + coordsText(p.loc) : ""));
            for (Player pl : Bukkit.getOnlinePlayers()) Fx.soundTo(plugin, pl, "BLOCK_NOTE_BLOCK_BASS", 1f, 0.6f);
        }

        // Preludio: clima, oscuridad, partículas y latidos en los últimos segundos
        int prelude = Math.max(3, c.getInt("world-boss.prelude-seconds", 12));
        if (secs <= prelude) {
            World w = p.loc.getWorld();
            if (!p.stormSet) {
                p.stormSet = true;
                w.setStorm(true);
                w.setThundering(true);
                w.setWeatherDuration(20 * (prelude + 180));
            }
            for (Player pl : w.getPlayers()) {
                pl.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));
                Fx.soundTo(plugin, pl, "ENTITY_WARDEN_HEARTBEAT", 1f, 0.6f);
            }
            Fx.particle(p.loc.clone().add(0, 1, 0), "SCULK_SOUL", 30, 3.0, 0.05);
        }
    }

    // ------------------------------------------------------------------
    //  Tick de jefes (1 segundo)
    // ------------------------------------------------------------------

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Boss> it = bosses.values().iterator();
        while (it.hasNext()) {
            Boss b = it.next();
            Entity ent = Bukkit.getEntity(b.id);
            if (!(ent instanceof LivingEntity e) || !e.isValid() || e.isDead()) {
                // Si el chunk está descargado la entidad no se encuentra: se descarta el registro (se readopta al cargar)
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(b.bar);
                it.remove();
                continue;
            }
            AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
            double max = hp == null ? 20 : hp.getValue();
            b.bar.progress((float) Math.max(0.0, Math.min(1.0, e.getHealth() / max)));
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player p : e.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(e.getLocation());
                if (d < 64 * 64) p.showBossBar(b.bar);
                else p.hideBossBar(b.bar);
                if (d < best && d < 40 * 40 && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.isDead()) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest == null) continue;
            if (e instanceof Mob m && m.getTarget() != nearest) m.setTarget(nearest);
            abilities(b, e, nearest, now);
        }
        if (bosses.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean ready(Boss b, String ability, long now) {
        double cd = cfg().getDouble("bosses." + b.key + ".abilities." + ability + ".cooldown", 20);
        Long next = b.cooldowns.get(ability);
        if (next != null && now < next) return false;
        b.cooldowns.put(ability, now + (long) (cd * 1000));
        return true;
    }

    private int amount(Boss b, String ability, int def) {
        int base = cfg().getInt("bosses." + b.key + ".abilities." + ability + ".amount", def);
        return (int) Math.max(1, Math.round(base * Math.min(3.0, plugin.nightmare().value("mob-count")) * (b.world ? 1.5 : 1.0)));
    }

    private void abilities(Boss b, LivingEntity e, Player t, long now) {
        double dist = e.getLocation().distance(t.getLocation());
        YamlConfiguration c = cfg();
        switch (b.key) {
            case "zombie_king" -> {
                if (ready(b, "summon", now)) minions(b, e, t, EntityType.ZOMBIE, amount(b, "summon", 5));
                if (ready(b, "wave", now)) {
                    plugin.hordes().start(t, List.of(EntityType.ZOMBIE, EntityType.HUSK), amount(b, "wave", 16), 4, 40, "boss:" + b.id);
                    Fx.actionBar(t, "&4El Zombie King convoca una ola...");
                }
            }
            case "spider_queen" -> {
                if (ready(b, "summon", now)) minions(b, e, t, EntityType.SPIDER, amount(b, "summon", 6));
                if (ready(b, "web", now)) {
                    plugin.spiders().place(t.getLocation().getBlock());
                    Fx.sound(plugin, t.getLocation(), "ENTITY_SPIDER_AMBIENT", 1f, 0.5f);
                }
                if (dist < 10 && ready(b, "poison", now)) {
                    int sec = Math.min(c.getInt("poison.max-duration", 180), c.getInt("poison.special-duration", 180));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, sec * 20, 0, false, true, true));
                    Fx.actionBar(t, "&2La Spider Queen te ha envenenado...");
                }
            }
            case "creeper_king" -> {
                if (ready(b, "summon", now)) {
                    minions(b, e, t, EntityType.CREEPER, amount(b, "summon", 4));
                }
                if (ready(b, "explosion", now) && plugin.perf().tryExplosion()) {
                    Location l = t.getLocation();
                    l.getWorld().createExplosion(l, (float) c.getDouble("bosses.creeper_king.abilities.explosion.power", 3.0), false,
                            c.getBoolean("bosses.creeper_king.abilities.explosion.break-blocks", false), e);
                }
                if (ready(b, "lightning", now)) {
                    Location l = t.getLocation().add(ThreadLocalRandom.current().nextDouble(-6, 6), 0, ThreadLocalRandom.current().nextDouble(-6, 6));
                    l.getWorld().strikeLightning(l);
                }
            }
            case "skeleton_sniper" -> {
                if (dist > 6 && ready(b, "snipe", now)) {
                    Location from = e.getEyeLocation();
                    Vector dir = t.getLocation().add(0, 1.0, 0).toVector().subtract(from.toVector()).normalize().multiply(3.2);
                    Arrow a = e.launchProjectile(Arrow.class, dir);
                    MobTags.setArrow(a, "explosive");
                    a.setDamage(c.getDouble("bosses.skeleton_sniper.abilities.snipe.damage", 12));
                    Fx.sound(plugin, from, "ENTITY_ARROW_SHOOT", 1.5f, 0.6f);
                }
                if (ready(b, "retreat", now) && dist < 5) {
                    e.setVelocity(e.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(1.2).setY(0.5));
                }
            }
            case "ender_beast" -> {
                if (dist > 5 && ready(b, "teleport", now)) {
                    Vector back = t.getLocation().getDirection().setY(0);
                    if (back.lengthSquared() < 1.0E-4) back = new Vector(1, 0, 0);
                    Location spot = plugin.spawns().spotNear(t.getLocation().add(back.normalize().multiply(-2.5)), 1.5);
                    Fx.particle(e.getLocation().add(0, 2, 0), "PORTAL", 40, 0.6, 0.3);
                    e.teleport(spot);
                    Fx.sound(plugin, spot, "ENTITY_ENDERMAN_TELEPORT", 1.5f, 0.5f);
                }
                if (ready(b, "aoe", now)) {
                    double r = c.getDouble("bosses.ender_beast.abilities.aoe.radius", 7);
                    double dmg = c.getDouble("bosses.ender_beast.abilities.aoe.damage", 10);
                    Fx.particle(e.getLocation().add(0, 1, 0), "PORTAL", 80, r / 2, 0.5);
                    for (Player p : e.getWorld().getNearbyPlayers(e.getLocation(), r)) {
                        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                        p.damage(dmg, e);
                        p.setVelocity(p.getLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.1).setY(0.6));
                    }
                }
                if (ready(b, "summon", now)) minions(b, e, t, EntityType.ENDERMAN, amount(b, "summon", 3));
            }
            case "mutated_warden" -> {
                if (e instanceof Warden w) w.setAnger(t, 150);
                if (ready(b, "shockwave", now)) {
                    double r = c.getDouble("bosses.mutated_warden.abilities.shockwave.radius", 10);
                    double dmg = c.getDouble("bosses.mutated_warden.abilities.shockwave.damage", 14);
                    Fx.particle(e.getLocation().add(0, 1, 0), "SCULK_SOUL", 80, r / 2, 0.05);
                    Fx.sound(plugin, e.getLocation(), "ENTITY_WARDEN_SONIC_BOOM", 2f, 0.7f);
                    for (Player p : e.getWorld().getNearbyPlayers(e.getLocation(), r)) {
                        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                        p.damage(dmg, e);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                    }
                }
            }
            default -> { }
        }
    }

    private void minions(Boss b, LivingEntity boss, Player target, EntityType type, int n) {
        String eventId = "boss:" + b.id;
        for (int i = 0; i < n; i++) {
            if (plugin.perf().countEvent(eventId) >= plugin.perf().maxEventMobs()) break;
            LivingEntity le = plugin.spawns().spawnAt(type, plugin.spawns().spotNear(boss.getLocation(), 6), target.getUniqueId(), eventId);
            if (le == null) break;
            if (le instanceof Creeper cr && ThreadLocalRandom.current().nextDouble() < 0.5) cr.setPowered(true);
            if (le instanceof Mob m) {
                m.setTarget(target);
                plugin.ai().track(m, true);
            }
        }
        Fx.particle(boss.getLocation().add(0, 1.5, 0), "SCULK_SOUL", 25, 0.8, 0.05);
        Fx.sound(plugin, boss.getLocation(), "ENTITY_EVOKER_PREPARE_SUMMON", 1.2f, 0.6f);
    }

    // ------------------------------------------------------------------
    //  Muerte
    // ------------------------------------------------------------------

    public void onDeath(LivingEntity e, Player killer) {
        finish(e.getUniqueId(), e.getLocation(), killer);
    }

    /** El Creeper King muere explotando (no genera EntityDeathEvent): se entregan recompensas igualmente. */
    public void onExploded(Creeper cr) {
        finish(cr.getUniqueId(), cr.getLocation(), null);
    }

    private void finish(UUID id, Location loc, Player killer) {
        Boss b = bosses.remove(id);
        if (b == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(b.bar);
        YamlConfiguration c = cfg();
        String name = Text.plain(c.getString("bosses." + b.key + ".name", b.key));
        Fx.broadcast("&6★ &e" + name + " &6ha sido derrotado" + (killer != null ? " por &f" + killer.getName() : "") + "&6.");
        Drops.roll(Drops.parse(c.getStringList("bosses." + b.key + ".drops")), loc, b.world ? 2.0 : 1.0);
        int xp = c.getInt("bosses." + b.key + ".xp", 300);
        loc.getWorld().spawn(loc, ExperienceOrb.class, o -> o.setExperience(xp));
        Fx.particle(loc.add(0, 1, 0), "EXPLOSION_EMITTER", 3, 1.0, 0);
        if (killer != null) plugin.playerManager().addThreat(killer, 5.0);
    }
}
