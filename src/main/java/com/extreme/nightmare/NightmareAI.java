package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IA ligera para los mobs ESPECIALES (no para todos): detección, persecución, memoria de la última posición,
 * prioridad a jugadores con poca vida, Endermen agresivos, animales hostiles y pollos explosivos.
 * Una sola tarea que solo existe mientras haya mobs rastreados.
 */
public class NightmareAI {

    private record Memory(Location loc, long time) {}

    private final ExtremePlugin plugin;
    private final Map<UUID, Mob> mobs = new LinkedHashMap<>();
    private final Map<UUID, Memory> memory = new HashMap<>();
    private final Map<UUID, Long> fuse = new HashMap<>();
    private final Map<UUID, Long> meleeCd = new HashMap<>();
    private BukkitTask task;
    private int cycle;

    public NightmareAI(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        mobs.clear();
        memory.clear();
        fuse.clear();
        meleeCd.clear();
    }

    public int tracked() { return mobs.size(); }

    public void track(Mob m, boolean aggressive) {
        if (mobs.size() >= cfg().getInt("ai.max-tracked", 200)) return;
        mobs.put(m.getUniqueId(), m);
        if (task == null) {
            long interval = Math.max(5L, cfg().getLong("ai.interval-ticks", 10L));
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        }
    }

    private void tick() {
        cycle++;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Mob>> it = mobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Mob> en = it.next();
            Mob m = en.getValue();
            if (!m.isValid() || m.isDead()) {
                memory.remove(en.getKey());
                fuse.remove(en.getKey());
                meleeCd.remove(en.getKey());
                it.remove();
                continue;
            }
            if (!plugin.nightmare().worldEnabled(m.getWorld())) continue;
            process(m, now);
        }
        if (mobs.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void process(Mob m, long now) {
        YamlConfiguration c = cfg();
        String type = MobTags.getType(m);
        boolean animal = "hostile_animal".equals(type) || "explosive_chicken".equals(type);
        double range = animal ? c.getDouble("animals.detection-range", 16)
                : "piglin_angry".equals(type) ? c.getDouble("piglins.detection-range", 30) : plugin.nightmare().detection();

        Player target = bestTarget(m, range, c.getDouble("ai.low-health-priority", 12));
        if (target != null) {
            if (m.getTarget() != target && !animal) m.setTarget(target);
            memory.put(m.getUniqueId(), new Memory(target.getLocation(), now));
        } else if (plugin.modules().on("nm_detection")) {
            Memory mem = memory.get(m.getUniqueId());
            long keep = (long) (c.getDouble("ai.memory-seconds", 20) * 1000);
            if (mem != null && now - mem.time() < keep) {
                searchLastKnown(m, mem);
            } else if (mem != null) {
                memory.remove(m.getUniqueId());
            }
        }

        if (MobTags.isElite(m) && cycle % 2 == 0) {
            Fx.particle(m.getLocation().add(0, 1, 0), c.getString("elite.particle", "FLAME"), 4, 0.35, 0.01);
        }

        if (type == null) return;
        switch (type) {
            case "enderman_hunter" -> { if (target != null) endermanBehavior(m, target, now); }
            case "hostile_animal" -> { if (target != null) animalAttack(m, target, now); }
            case "explosive_chicken" -> { if (target != null) chickenBehavior(m, target, now); else fuse.remove(m.getUniqueId()); }
            case "piglin_angry" -> { if (target != null) piglinBehavior(m, target, now); }
            case "ghast_demoniaco" -> {
                if (cycle % 2 == 0) Fx.particle(m.getLocation().add(0, 1, 0), c.getString("ghast.particle", "FLAME"), 8, 1.2, 0.02);
            }
            case "zombie_summoner" -> { if (target != null && m.getLocation().distanceSquared(target.getLocation()) < 28 * 28) summon(m, target); }
            default -> { }
        }
    }

    /** Elige el jugador más "atractivo": cercano y con poca vida. */
    private Player bestTarget(Mob m, double range, double lowHealthWeight) {
        Collection<Player> near = m.getWorld().getNearbyPlayers(m.getLocation(), range,
                p -> (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.isDead());
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        for (Player p : near) {
            double health = p.getHealth() / Math.max(1.0, p.getMaxHealth());
            double score = p.getLocation().distance(m.getLocation()) + health * lowHealthWeight;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** Recuerda dónde vio al jugador por última vez: va hacia allí y luego rastrea alrededor. */
    private void searchLastKnown(Mob m, Memory mem) {
        if (m.getTarget() != null && m.getTarget().isValid()) return;
        if (!m.getWorld().equals(mem.loc().getWorld())) return;
        double d = m.getLocation().distanceSquared(mem.loc());
        Location goal = mem.loc();
        if (d < 9) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            goal = mem.loc().clone().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
        }
        m.getPathfinder().moveTo(goal, 1.2);
    }

    // ------------------------------------------------------------------
    //  Comportamientos
    // ------------------------------------------------------------------

    private void endermanBehavior(Mob m, Player target, long now) {
        YamlConfiguration c = cfg();
        long next = MobTags.getLong(m, MobTags.NEXT_ABILITY);
        double dist = m.getLocation().distance(target.getLocation());
        if (now < next || dist < 5) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= c.getDouble("enderman.teleport-chance", 0.5)) return;

        Location spot;
        if (r.nextDouble() < c.getDouble("enderman.behind-chance", 0.5)) {
            Vector back = target.getLocation().getDirection().setY(0);
            if (back.lengthSquared() < 1.0E-4) back = new Vector(1, 0, 0);
            spot = target.getLocation().clone().add(back.normalize().multiply(-2.0));
            spot = plugin.spawns().spotNear(spot, 1.5);
        } else {
            spot = plugin.spawns().spotNear(target.getLocation(), 5);
        }
        Fx.particle(m.getLocation().add(0, 1, 0), "PORTAL", 20, 0.4, 0.2);
        m.teleport(spot);
        Fx.particle(spot.clone().add(0, 1, 0), "PORTAL", 20, 0.4, 0.2);
        Fx.sound(plugin, spot, "ENTITY_ENDERMAN_TELEPORT", 1f, 0.8f);
        m.setTarget(target);
        MobTags.setLong(m, MobTags.NEXT_ABILITY, now + (long) (c.getDouble("enderman.teleport-cooldown", 4) * 1000));
    }

    /** Piglins SIEMPRE enojados (con o sin oro): si el piglin no ataca por sí solo, persigue y golpea con ataque propio. */
    private void piglinBehavior(Mob m, Player target, long now) {
        YamlConfiguration c = cfg();
        if (m.getTarget() == target) return; // ya ataca con su propia IA
        m.getPathfinder().moveTo(target.getLocation(), c.getDouble("piglins.chase-speed", 1.3));
        if (m.getLocation().distanceSquared(target.getLocation()) > Math.pow(c.getDouble("piglins.reach", 2.2), 2)) return;
        Long cd = meleeCd.get(m.getUniqueId());
        if (cd != null && now < cd) return;
        meleeCd.put(m.getUniqueId(), now + (long) (c.getDouble("piglins.attack-cooldown", 1.0) * 1000));
        target.damage(c.getDouble("piglins.damage", 6.0) * plugin.nightmare().mult("damage"), m);
    }

    private void animalAttack(Mob m, Player target, long now) {
        YamlConfiguration c = cfg();
        m.getPathfinder().moveTo(target.getLocation(), c.getDouble("animals.chase-speed", 1.4));
        if (m.getLocation().distanceSquared(target.getLocation()) > Math.pow(c.getDouble("animals.reach", 2.2), 2)) return;
        Long cd = meleeCd.get(m.getUniqueId());
        if (cd != null && now < cd) return;
        meleeCd.put(m.getUniqueId(), now + (long) (c.getDouble("animals.attack-cooldown", 1.0) * 1000));
        double dmg = c.getDouble("animals.damage", 4.0) * plugin.nightmare().mult("damage");
        target.damage(dmg, m);
        Fx.sound(plugin, m.getLocation(), "ENTITY_PLAYER_ATTACK_STRONG", 0.8f, 0.8f);
    }

    private void chickenBehavior(Mob m, Player target, long now) {
        YamlConfiguration c = cfg();
        double d2 = m.getLocation().distanceSquared(target.getLocation());
        double trigger = c.getDouble("chicken.trigger-distance", 4.5);
        if (d2 > trigger * trigger) {
            fuse.remove(m.getUniqueId());
            m.getPathfinder().moveTo(target.getLocation(), c.getDouble("chicken.chase-speed", 1.5));
            return;
        }
        m.lookAt(target);
        Long start = fuse.get(m.getUniqueId());
        if (start == null) {
            fuse.put(m.getUniqueId(), now);
            Fx.sound(plugin, m.getLocation(), "ENTITY_CREEPER_PRIMED", 1f, 1.6f);
            return;
        }
        Fx.sound(plugin, m.getLocation(), "ENTITY_CHICKEN_HURT", 1f, 1.8f);
        Fx.particle(m.getLocation().add(0, 0.5, 0), "SMOKE", 6, 0.2, 0.02);
        if (now - start >= (long) (c.getDouble("chicken.countdown", 2.0) * 1000)) {
            explode(m, (float) (c.getDouble("chicken.power", 2.4) * Math.min(2.0, plugin.nightmare().mult("damage") * 0.5 + 0.5)),
                    c.getBoolean("chicken.break-blocks", false));
        }
    }

    private void explode(Mob m, float power, boolean breakBlocks) {
        fuse.remove(m.getUniqueId());
        Location loc = m.getLocation();
        m.remove();
        if (plugin.perf().tryExplosion()) {
            loc.getWorld().createExplosion(loc, power, false, breakBlocks, null);
        } else {
            Fx.particle(loc, "EXPLOSION_EMITTER", 1, 0, 0);
        }
    }

    /** Zombie Invocador: lanza una horda progresiva contra el jugador (con cooldown). */
    public void summon(Mob summoner, Player target) {
        YamlConfiguration c = cfg();
        long now = System.currentTimeMillis();
        if (!plugin.modules().on("nm_zombies")) return;
        if (now < MobTags.getLong(summoner, MobTags.NEXT_ABILITY)) return;
        if (!plugin.nightmare().worldEnabled(summoner.getWorld())) return;

        int total = (int) Math.round(c.getInt("zombies.summoner-total", 8) * plugin.nightmare().value("mob-count"));
        boolean started = plugin.hordes().start(target, List.of(EntityType.ZOMBIE), Math.min(total, 60),
                c.getInt("zombies.summoner-batch", 3), c.getInt("zombies.summoner-interval", 40),
                "summon:" + summoner.getUniqueId());
        if (!started) return;
        MobTags.setLong(summoner, MobTags.NEXT_ABILITY, now + (long) (c.getDouble("zombies.summoner-cooldown", 45) * 1000));
        Fx.sound(plugin, summoner.getLocation(), "ENTITY_EVOKER_PREPARE_SUMMON", 1f, 0.7f);
        Fx.particle(summoner.getLocation().add(0, 1.2, 0), "SCULK_SOUL", 25, 0.5, 0.05);
        Fx.actionBar(target, "&4⚠ &cUn Zombie Invocador convoca a su horda...");
    }

    /** Un mob golpeado por el jugador puede recordar su posición (llamado desde el listener). */
    public void noteAttacker(Mob m, Player attacker) {
        if (!plugin.modules().on("nm_detection")) return;
        memory.put(m.getUniqueId(), new Memory(attacker.getLocation(), System.currentTimeMillis()));
        if (!(m.getTarget() instanceof Player)) m.setTarget(attacker);
    }

    public boolean isTracked(Entity e) { return mobs.containsKey(e.getUniqueId()); }
}
