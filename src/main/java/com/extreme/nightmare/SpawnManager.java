package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Spawn personalizado alrededor de los jugadores. Una sola tarea periódica que procesa pocos jugadores por ciclo.
 * Al colocar mobs con su propia lógica de ubicación puede usar slabs, cuevas, estructuras y lugares "seguros".
 */
public class SpawnManager {

    private record Spec(EntityType type, double weight, int minLevel) {}

    private final ExtremePlugin plugin;
    private final List<Spec> specs = new ArrayList<>();
    private final Map<UUID, Long> nextSpawn = new HashMap<>();
    private BukkitTask task;
    private int rr;

    private boolean enabled;
    private int cycleTicks, maxPlayersPerCycle, attempts, searchUp, searchDown, minY, maxY;
    private double baseChance, minDist, maxDist, baseCount, ceilingChance;
    private long cooldownMs;
    private boolean daySpawns;
    private Set<String> worlds = new HashSet<>(), biomeWhite = new HashSet<>(), biomeBlack = new HashSet<>(), environments = new HashSet<>();
    private double totalWeight;

    public SpawnManager(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    public void reload() {
        YamlConfiguration c = cfg();
        String p = "spawn.";
        enabled = c.getBoolean(p + "enabled", true);
        cycleTicks = Math.max(20, c.getInt(p + "cycle-ticks", 100));
        maxPlayersPerCycle = Math.max(1, c.getInt(p + "max-players-per-cycle", 6));
        attempts = Math.max(1, c.getInt(p + "attempts", 6));
        searchUp = Math.max(0, c.getInt(p + "search-up", 5));
        searchDown = Math.max(0, c.getInt(p + "search-down", 12));
        minY = c.getInt(p + "min-y", -60);
        maxY = c.getInt(p + "max-y", 320);
        baseChance = c.getDouble(p + "chance", 0.35);
        baseCount = c.getDouble(p + "count", 2);
        minDist = c.getDouble(p + "min-distance", 14);
        maxDist = Math.max(minDist + 1, c.getDouble(p + "max-distance", 32));
        ceilingChance = c.getDouble(p + "spider-ceiling-chance", 0.25);
        cooldownMs = (long) (c.getDouble(p + "cooldown", 8) * 1000);
        daySpawns = c.getBoolean(p + "allow-daytime", false);
        worlds = lower(c.getStringList(p + "worlds"), false);
        biomeWhite = lower(c.getStringList(p + "biomes-whitelist"), true);
        biomeBlack = lower(c.getStringList(p + "biomes-blacklist"), true);
        environments = new HashSet<>(c.getStringList(p + "environments"));

        specs.clear();
        totalWeight = 0;
        ConfigurationSection mobs = c.getConfigurationSection(p + "mobs");
        if (mobs != null) {
            for (String key : mobs.getKeys(false)) {
                ConfigurationSection s = mobs.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    EntityType t = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                    Spec spec = new Spec(t, s.getDouble("weight", 1), s.getInt("min-level", 1));
                    specs.add(spec);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Mob desconocido en nightmare.yml spawn.mobs: " + key);
                }
            }
        }

        if (task != null) task.cancel();
        task = enabled && (plugin.modules().on("nm_spawn") || plugin.modules().on("nm_ghast")) && plugin.nightmare().isEnabled()
                ? Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, cycleTicks, cycleTicks) : null;
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    private Set<String> lower(List<String> list, boolean stripNamespace) {
        Set<String> out = new HashSet<>();
        for (String s : list) out.add(stripNamespace && s.contains(":") ? s.substring(s.indexOf(':') + 1).toLowerCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT));
        return out;
    }

    // ------------------------------------------------------------------
    //  Ciclo principal
    // ------------------------------------------------------------------

    private void cycle() {
        if (!plugin.nightmare().isEnabled() || !(plugin.modules().on("nm_spawn") || plugin.modules().on("nm_ghast"))) return;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty() || plugin.perf().total() >= plugin.perf().maxGlobal()) return;

        long now = System.currentTimeMillis();
        int processed = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < players.size() && processed < maxPlayersPerCycle; i++) {
            Player p = players.get(Math.floorMod(rr + i, players.size()));
            if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                processed++;
                netherTick(p, now);
                continue;
            }
            if (!plugin.modules().on("nm_spawn") || !eligible(p)) continue;
            processed++;
            Long next = nextSpawn.get(p.getUniqueId());
            if (next != null && now < next) continue;

            double chance = baseChance * plugin.nightmare().value("spawn-rate")
                    * plugin.vengeance().spawnMult(p) * plugin.zones().spawnMult(p) * plugin.playerManager().threatMult(p)
                    * plugin.events().spawnMult(p);
            if (r.nextDouble() >= Math.min(chance, 0.95)) continue;
            nextSpawn.put(p.getUniqueId(), now + cooldownMs);

            int count = (int) Math.max(1, Math.round(baseCount * plugin.nightmare().value("mob-count")
                    * plugin.vengeance().spawnMult(p)));
            int room = plugin.perf().maxPerPlayer() - plugin.perf().countOwner(p.getUniqueId());
            count = Math.min(count, Math.max(0, room));
            for (int n = 0; n < count && plugin.perf().total() < plugin.perf().maxGlobal(); n++) spawnOne(p);
        }
        rr += processed + 1;
        if (nextSpawn.size() > 200) nextSpawn.values().removeIf(t -> now - t > 120_000L);
    }

    private final Map<UUID, Long> nextNether = new HashMap<>();

    /** Nether: aparece el GHAST DEMONÍACO (explosión personalizada) en el aire cerca del jugador. */
    private void netherTick(Player p, long now) {
        if (!plugin.modules().on("nm_ghast") || !plugin.nightmare().worldEnabled(p.getWorld())) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        Long next = nextNether.get(p.getUniqueId());
        if (next != null && now < next) return;
        YamlConfiguration c = cfg();
        double chance = c.getDouble("ghast.nether-spawn-chance", 0.25) * plugin.nightmare().value("spawn-rate");
        if (ThreadLocalRandom.current().nextDouble() >= Math.min(chance, 0.9)) return;
        nextNether.put(p.getUniqueId(), now + (long) (c.getDouble("ghast.nether-cooldown", 20) * 1000));
        if (nextNether.size() > 100) nextNether.values().removeIf(t -> now - t > 300_000L);
        if (plugin.perf().countOwnerKey(p.getUniqueId(), "ghast") >= c.getInt("ghast.max-per-player", 2)) return;
        Location loc = findAirSpot(p, 18, 38);
        if (loc != null) spawnAt(EntityType.GHAST, loc, p.getUniqueId(), null);
    }

    /** Hueco de aire de 3x3x3 (el ghast es grande) a cierta distancia del jugador. */
    private Location findAirSpot(Player p, double min, double max) {
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int a = 0; a < 10; a++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getLocation().getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getLocation().getZ() + Math.sin(ang) * dist);
            int y = p.getLocation().getBlockY() + r.nextInt(2, 12);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || y < w.getMinHeight() + 2 || y > w.getMaxHeight() - 4) continue;
            boolean clear = true;
            for (int dx = -1; dx <= 1 && clear; dx++) for (int dy = -1; dy <= 1 && clear; dy++) for (int dz = -1; dz <= 1 && clear; dz++) {
                Block b = w.getBlockAt(x + dx, y + dy, z + dz);
                if (!b.isPassable() || b.isLiquid()) clear = false;
            }
            if (clear) return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private boolean eligible(Player p) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        World w = p.getWorld();
        if (!plugin.nightmare().worldEnabled(w)) return false;
        if (!worlds.isEmpty() && !worlds.contains(w.getName().toLowerCase(Locale.ROOT))) return false;
        if (!environments.isEmpty() && !environments.contains(w.getEnvironment().name())) return false;
        long t = w.getTime();
        boolean day = !(t >= 13000 && t < 23000);
        if (day && !daySpawns && p.getLocation().getBlock().getLightFromSky() > 2
                && !plugin.events().isActiveFor(p) && !plugin.zones().inZone(p) && !plugin.vengeance().isActive(p)) return false;
        return true;
    }

    private void spawnOne(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location loc = findLocation(p, minDist, maxDist);
        if (loc == null) return;

        YamlConfiguration c = cfg();
        boolean underground = loc.getY() < loc.getWorld().getHighestBlockYAt(loc) - 2;

        // Wardens en cuevas (no requieren Ancient City)
        if (underground && plugin.modules().on("nm_wardens") && c.getBoolean("wardens.normal-cave-spawn", true)
                && r.nextDouble() < c.getDouble("wardens.chance", 0.004) * plugin.nightmare().value("warden-chance")) {
            double y = loc.getY();
            boolean deep = y < c.getDouble("wardens.deep-y", 0);
            if (deep || c.getBoolean("wardens.normal-cave-spawn", true)) {
                plugin.bosses().trySpawnWarden(loc, r.nextDouble() < c.getDouble("wardens.mutated-chance", 0.25));
                return;
            }
        }

        // Mini-jefes
        double miniChance = plugin.nightmare().value("miniboss-chance") * plugin.vengeance().bossMult(p) * plugin.zones().bossMult(p);
        if (plugin.modules().on("nm_bosses") && r.nextDouble() < miniChance && plugin.bosses().canSpawnMore()) {
            plugin.bosses().spawnRandomMini(loc);
            return;
        }

        EntityType type = pick();
        if (type == null) return;
        if (plugin.modules().on("nm_spiders") && (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) && r.nextDouble() < ceilingChance) {
            Location ceil = ceilingSpot(loc);
            if (ceil != null) loc = ceil;
        }
        spawnAt(type, loc, p.getUniqueId(), null);
    }

    private EntityType pick() {
        int level = plugin.nightmare().getLevel();
        double total = 0;
        for (Spec s : specs) if (level >= s.minLevel()) total += s.weight();
        if (total <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (Spec s : specs) {
            if (level < s.minLevel()) continue;
            roll -= s.weight();
            if (roll <= 0) return s.type();
        }
        return specs.get(0).type();
    }

    // ------------------------------------------------------------------
    //  Ubicaciones
    // ------------------------------------------------------------------

    /** Busca un lugar válido (suelo sólido o slab, 2 bloques libres) en un anillo alrededor del jugador. */
    public Location findLocation(Player p, double min, double max) {
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int py = p.getLocation().getBlockY();
        for (int a = 0; a < attempts; a++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int bx = (int) Math.floor(p.getLocation().getX() + Math.cos(ang) * dist);
            int bz = (int) Math.floor(p.getLocation().getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            for (int dy = searchUp; dy >= -searchDown; dy--) {
                int y = py + dy;
                if (y < minY || y > maxY || y < w.getMinHeight() + 1 || y > w.getMaxHeight() - 3) continue;
                Block floor = w.getBlockAt(bx, y - 1, bz);
                if (!floor.getType().isSolid()) continue;
                Block a1 = w.getBlockAt(bx, y, bz), a2 = w.getBlockAt(bx, y + 1, bz);
                if (!free(a1) || !free(a2)) continue;
                if (!biomeOk(w, bx, y, bz)) break;
                return new Location(w, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    private boolean free(Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    private boolean biomeOk(World w, int x, int y, int z) {
        if (biomeWhite.isEmpty() && biomeBlack.isEmpty()) return true;
        String key = w.getBiome(x, y, z).getKey().getKey().toLowerCase(Locale.ROOT);
        if (!biomeWhite.isEmpty() && !biomeWhite.contains(key)) return false;
        return !biomeBlack.contains(key);
    }

    /** Punto libre cercano a una ubicación (para hijos de multiplicación, refuerzos, etc.). */
    public Location spotNear(Location center, double radius) {
        World w = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double d = 1.5 + r.nextDouble() * Math.max(0.5, radius - 1.5);
            Location l = center.clone().add(Math.cos(ang) * d, 0, Math.sin(ang) * d);
            for (int dy = 1; dy >= -2; dy--) {
                Block feet = w.getBlockAt(l.getBlockX(), center.getBlockY() + dy, l.getBlockZ());
                Block head = feet.getRelative(0, 1, 0);
                if (free(feet) && free(head) && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return new Location(w, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return center.clone();
    }

    private Location ceilingSpot(Location loc) {
        World w = loc.getWorld();
        for (int dy = 3; dy <= 10; dy++) {
            Block b = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + dy, loc.getBlockZ());
            if (b.getType().isSolid()) {
                Block below = b.getRelative(0, -1, 0);
                if (free(below)) return new Location(w, below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Creación de mobs
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Entity spawnRaw(EntityType type, Location loc, Consumer<Entity> pre) {
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || loc.getWorld() == null) return null;
        return loc.getWorld().spawn(loc, (Class<Entity>) cls, pre);
    }

    /** Crea un mob del plugin respetando los límites globales. Devuelve null si no se pudo. */
    public LivingEntity spawnAt(EntityType type, Location loc, UUID owner, String eventId) {
        if (plugin.perf().total() >= plugin.perf().maxGlobal()) return null;
        Entity e = spawnRaw(type, loc, ent -> {
            MobTags.setOurs(ent);
            if (eventId != null) MobTags.setEvent(ent, eventId);
            if (ent instanceof Mob m) m.setRemoveWhenFarAway(true);
        });
        if (!(e instanceof LivingEntity le)) return null;
        plugin.perf().track(le, owner, eventId, type.name().toLowerCase(Locale.ROOT));
        return le;
    }

    /** Genera n mobs alrededor de un jugador (para hordas y eventos). */
    public List<LivingEntity> spawnAround(Player p, EntityType type, int n, String eventId, double min, double max) {
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (eventId != null && plugin.perf().countEvent(eventId) >= plugin.perf().maxEventMobs()) break;
            if (plugin.perf().countOwner(p.getUniqueId()) >= plugin.perf().maxPerPlayer() + 20) break;
            Location loc = findLocation(p, min, max);
            if (loc == null) continue;
            LivingEntity le = spawnAt(type, loc, p.getUniqueId(), eventId);
            if (le != null) out.add(le);
        }
        return out;
    }
}
