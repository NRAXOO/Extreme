package com.extreme.nightmare;

import com.extreme.ExtremePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.loot.Lootable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Sueño, estructuras, cofres trampa, rayos y clima. Todo basado en eventos + una tarea ligera de estructuras. */
public class WorldListener implements Listener {

    private final ExtremePlugin plugin;
    private final Map<UUID, Long> nightLock = new HashMap<>();
    private final Map<String, Long> structureCd = new HashMap<>();
    private BukkitTask structureTask;

    public WorldListener(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() { return plugin.configs().get("nightmare.yml"); }

    private boolean roll(double c) { return ThreadLocalRandom.current().nextDouble() < c; }

    public void reload() {
        shutdown();
        if (!cfg().getBoolean("structures.enabled", true) || !plugin.modules().on("nm_structures") || !plugin.nightmare().isEnabled()) return;
        long t = Math.max(40L, cfg().getLong("structures.check-interval", 100L));
        structureTask = Bukkit.getScheduler().runTaskTimer(plugin, this::structureTick, t, t);
    }

    public void shutdown() {
        if (structureTask != null) structureTask.cancel();
        structureTask = null;
    }

    // ------------------------------------------------------------------
    //  Dormir
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        Player p = e.getPlayer();
        if (!plugin.nightmare().worldEnabled(p.getWorld()) || !plugin.modules().on("nm_sleep") || !cfg().getBoolean("sleep.enabled", true)) return;
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        YamlConfiguration c = cfg();
        long now = System.currentTimeMillis();

        Long lock = nightLock.get(p.getWorld().getUID());
        if (lock != null && now < lock) {
            e.setCancelled(true);
            Fx.actionBar(p, "&4La noche se niega a terminar. No puedes dormir...");
            return;
        }

        double level = plugin.nightmare().value("event-frequency");
        if (roll(c.getDouble("sleep.fail-chance", 0.25) * Math.min(2.0, level))) {
            e.setCancelled(true);
            Fx.title(p, "&4No puedes dormir...", "&cAlgo te observa desde la oscuridad.");
            Fx.soundTo(plugin, p, "ENTITY_GHAST_AMBIENT", 1f, 0.5f);
            if (roll(c.getDouble("sleep.ambush-on-fail-chance", 0.5))) ambush(p, "No puedes dormir");
            return;
        }
        if (roll(c.getDouble("sleep.ambush-chance", 0.2) * Math.min(2.0, level))) {
            // Te emboscan mientras duermes: despiertas con enemigos alrededor de la cama
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || !p.isSleeping()) return;
                p.wakeup(true);
                ambush(p, "Te han emboscado");
            }, c.getLong("sleep.ambush-delay-ticks", 60L));
        }
    }

    private void ambush(Player p, String why) {
        int n = (int) Math.round(cfg().getInt("sleep.ambush-mobs", 8) * plugin.nightmare().value("mob-count"));
        plugin.hordes().start(p, List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER),
                Math.min(n, 40), 3, 20, "ambush:" + p.getUniqueId());
        Fx.title(p, "&4&l¡EMBOSCADA!", "&c" + why + "...");
        Fx.soundTo(plugin, p, "ENTITY_WITHER_SPAWN", 0.7f, 1.4f);
    }

    /** Algunas noches duran más: se cancela el salto de noche y se bloquea dormir un rato. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent e) {
        World w = e.getWorld();
        if (e.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP || !plugin.nightmare().worldEnabled(w) || !plugin.modules().on("nm_sleep")) return;
        YamlConfiguration c = cfg();
        if (!c.getBoolean("sleep.enabled", true) || !roll(c.getDouble("sleep.long-night-chance", 0.25))) return;
        e.setCancelled(true);
        nightLock.put(w.getUID(), System.currentTimeMillis() + c.getLong("sleep.long-night-lock-seconds", 180) * 1000L);
        for (Player p : w.getPlayers()) {
            if (p.isSleeping()) p.wakeup(false);
            Fx.title(p, "&4La noche se alarga...", "&cEl amanecer tardará en llegar.");
        }
        Fx.chat(w.getPlayers(), "&4La noche se niega a terminar...");
    }

    // ------------------------------------------------------------------
    //  Cofres trampa y mini-jefes escondidos
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChest(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        Material m = b.getType();
        if (m != Material.CHEST && m != Material.TRAPPED_CHEST && m != Material.BARREL) return;
        Player p = e.getPlayer();
        if (!plugin.nightmare().worldEnabled(p.getWorld()) || !plugin.modules().on("nm_structures") || !cfg().getBoolean("structures.chest-traps.enabled", true)) return;
        BlockState st = b.getState();
        if (!(st instanceof Lootable l) || !l.hasLootTable()) return; // solo cofres de estructura sin abrir

        YamlConfiguration c = cfg();
        if (!roll(c.getDouble("structures.chest-traps.chance", 0.2) * Math.min(3.0, plugin.nightmare().value("event-frequency")))) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ambush = c.getDouble("structures.chest-traps.weights.ambush", 50), poison = c.getDouble("structures.chest-traps.weights.poison", 20),
                boom = c.getDouble("structures.chest-traps.weights.explosion", 15), boss = c.getDouble("structures.chest-traps.weights.miniboss", 15);
        double roll = r.nextDouble() * (ambush + poison + boom + boss);
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        if ((roll -= ambush) < 0) {
            ambush(p, "El cofre estaba maldito");
        } else if ((roll -= poison) < 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * Math.min(c.getInt("poison.max-duration", 180), 40), 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 8, 0));
            Fx.particle(loc, "SMOKE", 40, 0.5, 0.05);
            Fx.actionBar(p, "&2Un gas tóxico escapa del cofre...");
        } else if ((roll -= boom) < 0) {
            if (plugin.perf().tryExplosion()) loc.getWorld().createExplosion(loc, 2.0f, false, false, null);
        } else if (plugin.bosses().canSpawnMore()) {
            plugin.bosses().spawnRandomMini(plugin.spawns().spotNear(loc, 4));
        }
    }

    // ------------------------------------------------------------------
    //  Clima / rayos / jefes persistentes
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        plugin.weather().onLightning(e.getLightning().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState()) plugin.weather().onStormStart(e.getWorld());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (MobTags.getBoss(en) != null) plugin.bosses().adopt(en);
            else if (en instanceof org.bukkit.entity.Mob m) plugin.nmMobs().readopt(m);
        }
    }

    // ------------------------------------------------------------------
    //  Estructuras: Ancient City, Fortaleza del Nether, Aldeas
    // ------------------------------------------------------------------

    private boolean cooldownOk(Player p, String type, long seconds) {
        String key = p.getUniqueId() + ":" + type;
        long now = System.currentTimeMillis();
        Long next = structureCd.get(key);
        if (next != null && now < next) return false;
        structureCd.put(key, now + seconds * 1000L);
        if (structureCd.size() > 300) structureCd.values().removeIf(t -> now > t);
        return true;
    }

    private void structureTick() {
        YamlConfiguration c = cfg();
        if (!plugin.nightmare().isEnabled()) return;
        long cd = c.getLong("structures.cooldown", 600);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) || !plugin.nightmare().worldEnabled(p.getWorld())) continue;
            Location l = p.getLocation();
            World w = p.getWorld();

            if (w.getEnvironment() == World.Environment.NORMAL && c.getBoolean("structures.ancient-city.enabled", true)
                    && l.getBlockY() < 0 && "deep_dark".equals(w.getBiome(l).getKey().getKey())) {
                if (cooldownOk(p, "city", cd)) {
                    Fx.title(p, "&5&lEL SILENCIO SE ROMPE", "&dLa Ancient City ha despertado...");
                    plugin.events().start(c.getString("structures.ancient-city.event", "darkness"), p, true);
                    if (roll(c.getDouble("structures.ancient-city.miniboss-chance", 0.3)) && plugin.bosses().canSpawnMore()) {
                        plugin.bosses().trySpawnWarden(plugin.spawns().spotNear(l, 10), true);
                    }
                }
            } else if (w.getEnvironment() == World.Environment.NETHER && c.getBoolean("structures.fortress.enabled", true)
                    && l.clone().subtract(0, 1, 0).getBlock().getType().name().contains("NETHER_BRICK")) {
                if (cooldownOk(p, "fortress", cd / 2)) {
                    List<EntityType> mobs = new ArrayList<>();
                    for (String s : c.getStringList("structures.fortress.mobs")) {
                        try {
                            mobs.add(EntityType.valueOf(s.toUpperCase(Locale.ROOT)));
                        } catch (IllegalArgumentException ignored) {
                            // mob inválido
                        }
                    }
                    if (!mobs.isEmpty()) {
                        int n = (int) Math.round(c.getInt("structures.fortress.mobs-amount", 10) * plugin.nightmare().value("mob-count"));
                        plugin.hordes().start(p, mobs, Math.min(n, 40), 4, 40, "fortress:" + p.getUniqueId());
                        Fx.title(p, "&c&lLA FORTALEZA TE REPELE", "&4Una horda se aproxima...");
                    }
                }
            } else if (w.getEnvironment() == World.Environment.NORMAL && c.getBoolean("structures.village.enabled", true)
                    && roll(c.getDouble("structures.village.scan-chance", 0.25))) {
                int villagers = 0;
                for (Entity en : p.getNearbyEntities(24, 12, 24)) if (en instanceof Villager) villagers++;
                if (villagers >= c.getInt("structures.village.min-villagers", 3) && cooldownOk(p, "village", cd)) {
                    int n = (int) Math.round(c.getInt("structures.village.mobs", 14) * plugin.nightmare().value("mob-count"));
                    plugin.hordes().start(p, List.of(EntityType.ZOMBIE, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.SPIDER),
                            Math.min(n, 50), 4, 40, "village:" + p.getUniqueId());
                    Fx.title(p, "&4&l¡INVASIÓN!", "&cLa aldea está siendo atacada. Defiende a los aldeanos.");
                }
            }
        }
    }
}
