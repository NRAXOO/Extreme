package com.extreme.lives;

import com.extreme.ExtremePlugin;
import com.extreme.utils.DataFile;
import com.extreme.utils.Text;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Sistema de vidas: datos en players.yml (por UUID), muertes, eliminación y baneo. */
public class LivesManager {

    private final ExtremePlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();
    private boolean dirty;

    // Caché de configuración (se rellena en reload())
    private boolean enabled = true;
    private int defaultLives = 5;
    private int maxLives = 10;
    private boolean banOnZero = true;
    private long cooldownMs = 3000L;
    private int killReward = 0;
    private Set<String> ignoreWorlds = new HashSet<>();
    private Set<EntityDamageEvent.DamageCause> ignoreCauses = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);

    public LivesManager(ExtremePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    // ------------------------------------------------------------------
    //  Carga / guardado
    // ------------------------------------------------------------------

    public void load() {
        data.clear();
        byName.clear();
        YamlConfiguration y = plugin.io().load(file);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s == null) continue;
                PlayerData d = new PlayerData(id, s.getString("name", ""), s.getInt("lives", 5));
                data.put(id, d);
                if (!d.name.isEmpty()) byName.put(d.name.toLowerCase(Locale.ROOT), id);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("UUID inválido en players.yml: " + key);
            }
        }
    }

    public void reload() {
        YamlConfiguration c = plugin.configs().get("lives.yml");
        enabled = c.getBoolean("lives.enabled", true);
        defaultLives = Math.max(1, c.getInt("lives.default", 5));
        maxLives = Math.max(defaultLives, c.getInt("lives.maximum", 10));
        banOnZero = c.getBoolean("lives.ban-on-zero", true);
        cooldownMs = Math.max(0L, c.getLong("lives.death-cooldown", 3L) * 1000L);
        killReward = Math.max(0, c.getInt("lives.pvp-kill-reward", 0));
        ignoreWorlds = new HashSet<>(c.getStringList("lives.ignore-worlds"));
        Set<EntityDamageEvent.DamageCause> causes = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
        for (String s : c.getStringList("lives.ignore-causes")) {
            try {
                causes.add(EntityDamageEvent.DamageCause.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Causa desconocida en lives.yml (ignore-causes): " + s);
            }
        }
        ignoreCauses = causes;
    }

    /** Guarda solo si hubo cambios. La serialización es rápida; la escritura va a otro hilo. */
    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (PlayerData d : data.values()) {
            String base = "players." + d.id;
            y.set(base + ".name", d.name);
            y.set(base + ".lives", d.lives);
        }
        plugin.io().saveAsync(file, y.saveToString());
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public int getDefaultLives() { return defaultLives; }
    public int getMaxLives() { return maxLives; }
    public boolean isBanOnZero() { return banOnZero; }

    public PlayerData getOrCreate(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData(p.getUniqueId(), p.getName(), defaultLives);
            data.put(d.id, d);
            dirty = true;
        }
        if (!p.getName().equals(d.name)) {
            byName.remove(d.name.toLowerCase(Locale.ROOT));
            d.name = p.getName();
            dirty = true;
        }
        byName.put(d.name.toLowerCase(Locale.ROOT), d.id);
        return d;
    }

    public PlayerData find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return getOrCreate(online);
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : data.get(id);
    }

    public int getLives(UUID id) {
        PlayerData d = data.get(id);
        return d == null ? defaultLives : d.lives;
    }

    public List<PlayerData> all() {
        List<PlayerData> list = new ArrayList<>(data.values());
        list.sort(Comparator.comparing((PlayerData d) -> d.name.toLowerCase(Locale.ROOT)));
        return list;
    }

    public List<PlayerData> allByLives() {
        List<PlayerData> list = new ArrayList<>(data.values());
        list.sort(Comparator.comparingInt((PlayerData d) -> -d.lives)
                .thenComparing(d -> d.name.toLowerCase(Locale.ROOT)));
        return list;
    }

    // ------------------------------------------------------------------
    //  Modificación (comandos / GUI)
    // ------------------------------------------------------------------

    /** Fija las vidas (limitadas a 0..máximo). Si queda en 0 y el jugador está conectado, se le elimina. */
    public int setLives(PlayerData d, int amount) {
        int value = Math.max(0, Math.min(amount, maxLives));
        d.lives = value;
        dirty = true;
        if (enabled && value == 0) {
            Player online = Bukkit.getPlayer(d.id);
            if (online != null) eliminate(online);
        }
        return value;
    }

    public int addLives(PlayerData d, int delta) {
        return setLives(d, d.lives + delta);
    }

    public int resetLives(PlayerData d) {
        return setLives(d, defaultLives);
    }

    public void setEnabled(boolean on) {
        plugin.configs().set("lives.yml", "lives.enabled", on);
        enabled = on;
    }

    // ------------------------------------------------------------------
    //  Eventos
    // ------------------------------------------------------------------

    public void handleJoin(Player p) {
        PlayerData d = getOrCreate(p);
        if (d.lives > maxLives) {
            d.lives = maxLives;
            dirty = true;
        }
        if (enabled && d.lives <= 0) {
            // Entró con 0 vidas: el ban expiró o fue levantado por un admin
            if (plugin.configs().get("lives.yml").getBoolean("ban.restore-lives-on-join", true)) {
                d.lives = defaultLives;
                dirty = true;
                plugin.messages().send(p, "lives-restored", "lives", d.lives);
            } else {
                eliminate(p);
            }
        }
    }

    public void handleQuit(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d != null) d.name = p.getName();
        saveIfDirty(); // guarda antes de que se pierda el estado del jugador
    }

    public void handleDeath(PlayerDeathEvent e) {
        if (!enabled) return;
        Player p = e.getEntity();
        if (p.hasPermission("extreme.lives.exempt")) return;
        if (ignoreWorlds.contains(p.getWorld().getName())) return;

        EntityDamageEvent last = p.getLastDamageCause();
        if (last != null && ignoreCauses.contains(last.getCause())) return;

        PlayerData d = getOrCreate(p);
        long now = System.currentTimeMillis();
        if (now - d.lastDeath < cooldownMs) return; // misma muerte / evento duplicado
        d.lastDeath = now;
        if (d.lives <= 0) return;                    // ya está eliminado

        d.lives--;
        dirty = true;

        Player killer = p.getKiller();
        if (killer != null && !killer.equals(p) && killReward > 0 && enabled) {
            PlayerData kd = getOrCreate(killer);
            if (kd.lives < maxLives && kd.lives > 0) {
                kd.lives = Math.min(maxLives, kd.lives + killReward);
                plugin.messages().send(killer, "lives-gained", "lives", kd.lives, "player", p.getName());
                plugin.sounds().playTo(killer, "life-gained");
            }
        }

        if (d.lives <= 0) {
            eliminate(p);
        } else {
            plugin.messages().send(p, "lives-lost", "lives", d.lives, "max_lives", maxLives);
            plugin.sounds().playTo(p, "life-lost");
            saveIfDirty();
        }
    }

    // ------------------------------------------------------------------
    //  Eliminación y baneo
    // ------------------------------------------------------------------

    /** Registra la eliminación y (si está configurado) banea al jugador. */
    public void eliminate(Player p) {
        PlayerData d = getOrCreate(p);
        d.lives = 0;
        dirty = true;
        saveIfDirty();

        plugin.messages().send(p, "lives-zero");
        plugin.messages().broadcast("lives-zero-broadcast", "player", p.getName());
        plugin.sounds().playAll("lives-zero");

        YamlConfiguration c = plugin.configs().get("lives.yml");
        if (!banOnZero || !c.getBoolean("ban.enabled", true)) return;

        long delay = Math.max(1L, c.getLong("ban.delay-ticks", 5L));
        UUID id = p.getUniqueId();
        String name = p.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> executeBan(id, name), delay);
    }

    private void executeBan(UUID id, String name) {
        YamlConfiguration c = plugin.configs().get("lives.yml");
        Player p = Bukkit.getPlayer(id);

        String kickAmp = plugin.messages().str("ban-message", "player", name);
        String reason = Text.section(kickAmp);
        boolean permanent = c.getBoolean("ban.permanent", true);
        Date expires = permanent ? null
                : new Date(System.currentTimeMillis() + TimeUtil.parse(c.getString("ban.duration"), 7L * 86_400_000L));

        boolean done = banViaApi(p, id, name, reason, expires);
        if (!done) {
            String cmd = c.getString("ban.fallback-command", "ban {player} {reason}")
                    .replace("{player}", name)
                    .replace("{reason}", Text.plain(kickAmp).replace('\n', ' '));
            plugin.getLogger().warning("La API de baneo falló; ejecutando comando de respaldo: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        if (p != null && p.isOnline()) p.kick(Text.color(kickAmp));
    }

    /** Baneo por perfil usando reflexión para ser compatible con distintos nombres de API entre versiones. */
    private boolean banViaApi(Player p, UUID id, String name, String reason, Date expires) {
        try {
            Object profile = p != null ? p.getPlayerProfile() : Bukkit.createProfile(id, name);
            for (String className : new String[]{"org.bukkit.BanList$Type", "org.bukkit.BanListType"}) {
                Class<?> typeClass;
                try {
                    typeClass = Class.forName(className);
                } catch (ClassNotFoundException ex) {
                    continue;
                }
                Object profileType = null;
                for (Object constant : typeClass.getEnumConstants()) {
                    if (constant.toString().equals("PROFILE")) profileType = constant;
                }
                if (profileType == null) continue;
                Method getList = Bukkit.class.getMethod("getBanList", typeClass);
                Object list = getList.invoke(null, profileType);
                Method add = Class.forName("org.bukkit.BanList")
                        .getMethod("addBan", Object.class, String.class, Date.class, String.class);
                add.invoke(list, profile, reason, expires, "Extreme");
                return true;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "No se pudo banear mediante la API: " + t, t);
        }
        return false;
    }
}
