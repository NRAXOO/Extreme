package com.extreme;

import com.extreme.actionbar.ActionBarManager;
import com.extreme.commands.AdminCommand;
import com.extreme.commands.DayCommand;
import com.extreme.commands.ExtremeCommand;
import com.extreme.commands.LivesCommand;
import com.extreme.commands.PvpCommand;
import com.extreme.config.ConfigManager;
import com.extreme.config.Messages;
import com.extreme.config.StateStore;
import com.extreme.day.DayManager;
import com.extreme.difficulty.DifficultyListener;
import com.extreme.difficulty.DifficultyManager;
import com.extreme.gui.MenuListener;
import com.extreme.lives.LivesListener;
import com.extreme.lives.LivesManager;
import com.extreme.commands.NightmareCommand;
import com.extreme.commands.RevengeCommand;
import com.extreme.modules.ModuleManager;
import com.extreme.mobs.MobEffectsManager;
import com.extreme.nightmare.AcidRainManager;
import com.extreme.nightmare.BossManager;
import com.extreme.nightmare.CursedZoneManager;
import com.extreme.nightmare.EventManager;
import com.extreme.nightmare.HordeManager;
import com.extreme.nightmare.MobTags;
import com.extreme.nightmare.MultiplicationManager;
import com.extreme.nightmare.NightmareAI;
import com.extreme.nightmare.NightmareListener;
import com.extreme.nightmare.NightmareManager;
import com.extreme.nightmare.NightmareMobManager;
import com.extreme.nightmare.PerformanceManager;
import com.extreme.nightmare.PlayerManager;
import com.extreme.nightmare.SpawnManager;
import com.extreme.nightmare.VengeanceManager;
import com.extreme.nightmare.WeatherManager;
import com.extreme.nightmare.WorldListener;
import com.extreme.revenge.RevengeListener;
import com.extreme.revenge.RevengeManager;
import com.extreme.mobs.MobListener;
import com.extreme.mobs.MobManager;
import com.extreme.mobs.SpiderManager;
import com.extreme.placeholder.ExtremeExpansion;
import com.extreme.pvp.PVPListener;
import com.extreme.pvp.PVPManager;
import com.extreme.utils.DataFile;
import com.extreme.utils.SoundManager;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ExtremePlugin extends JavaPlugin {

    private DataFile io;
    private ConfigManager configs;
    private Messages messages;
    private SoundManager sounds;
    private StateStore state;
    private DayManager days;
    private DifficultyManager difficulty;
    private PVPManager pvp;
    private LivesManager lives;
    private ActionBarManager actionBar;
    private MobManager mobs;
    private SpiderManager spiders;
    private MobEffectsManager effects;
    private BukkitTask autosaveTask;
    private ModuleManager modules;

    // --- Modo Pesadilla ---
    private NightmareManager nightmare;
    private PerformanceManager perf;
    private SpawnManager spawns;
    private HordeManager hordes;
    private NightmareMobManager nmMobs;
    private NightmareAI ai;
    private MultiplicationManager multiplication;
    private EventManager events;
    private BossManager bosses;
    private WeatherManager weather;
    private AcidRainManager acidRain;
    private PlayerManager playerManager;
    private CursedZoneManager zones;
    private VengeanceManager vengeance;
    private WorldListener worldListener;
    // --- Venganza entre jugadores ---
    private RevengeManager revenge;

    @Override
    public void onEnable() {
        MobTags.init(this);
        io = new DataFile(this);
        configs = new ConfigManager(this);
        for (ConfigManager.ConfigError er : configs.load(false)) {
            getLogger().severe("Error de configuración -> " + er.describe() + " (se usan los valores por defecto del jar)");
        }

        messages = new Messages(this);
        sounds = new SoundManager(this);
        state = new StateStore(this);
        state.load();

        modules = new ModuleManager(this);
        days = new DayManager(this);
        difficulty = new DifficultyManager(this);
        pvp = new PVPManager(this);
        lives = new LivesManager(this);
        actionBar = new ActionBarManager(this);
        mobs = new MobManager(this);
        spiders = new SpiderManager(this);
        effects = new MobEffectsManager(this);

        nightmare = new NightmareManager(this);
        perf = new PerformanceManager(this);
        spawns = new SpawnManager(this);
        hordes = new HordeManager(this);
        nmMobs = new NightmareMobManager(this);
        ai = new NightmareAI(this);
        multiplication = new MultiplicationManager(this);
        events = new EventManager(this);
        bosses = new BossManager(this);
        weather = new WeatherManager(this);
        acidRain = new AcidRainManager(this);
        playerManager = new PlayerManager(this);
        zones = new CursedZoneManager(this);
        vengeance = new VengeanceManager(this);
        worldListener = new WorldListener(this);
        revenge = new RevengeManager(this);

        lives.load();
        revenge.load();
        for (ConfigManager.ConfigError er : applyConfig()) {
            getLogger().severe("Error al aplicar configuración -> " + er.describe());
        }

        Bukkit.getPluginManager().registerEvents(new PVPListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DifficultyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LivesListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MobListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new NightmareListener(this), this);
        Bukkit.getPluginManager().registerEvents(worldListener, this);
        Bukkit.getPluginManager().registerEvents(playerManager, this);
        Bukkit.getPluginManager().registerEvents(new RevengeListener(this), this);

        bind("extreme", new ExtremeCommand(this));
        bind("pvp", new PvpCommand(this));
        bind("vidas", new LivesCommand(this));
        bind("dia", new DayCommand(this));
        bind("panel", new AdminCommand(this));
        bind("pesadilla", new NightmareCommand(this));
        bind("venganza", new RevengeCommand(this));

        // Re-registra jefes ya cargados en los mundos
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity en : w.getEntities()) if (MobTags.getBoss(en) != null) bosses.adopt(en);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ExtremeExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado: placeholders %extreme_*% registrados.");
        }
        getLogger().info("Extreme habilitado.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) autosaveTask.cancel();
        if (nightmare != null) nightmare.shutdown();
        if (perf != null) perf.shutdown();
        if (spawns != null) spawns.shutdown();
        if (hordes != null) hordes.shutdown();
        if (ai != null) ai.shutdown();
        if (events != null) events.shutdown();
        if (bosses != null) bosses.shutdown();
        if (weather != null) weather.shutdown();
        if (acidRain != null) acidRain.shutdown();
        if (zones != null) zones.shutdown();
        if (worldListener != null) worldListener.shutdown();
        if (revenge != null) revenge.shutdown();
        if (pvp != null) pvp.shutdown();
        if (days != null) days.shutdown();
        if (actionBar != null) actionBar.shutdown();
        if (spiders != null) spiders.cleanup();
        if (lives != null) lives.saveIfDirty();
        if (state != null) state.save();
        if (io != null) io.shutdown();
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Comando no declarado en plugin.yml: " + name);
            return;
        }
        c.setExecutor(executor);
        if (executor instanceof TabCompleter tc) c.setTabCompleter(tc);
    }

    // ------------------------------------------------------------------
    //  Recarga
    // ------------------------------------------------------------------

    /** Relee todos los archivos. Si hay errores de YAML no cambia nada y devuelve la lista de errores. */
    public List<ConfigManager.ConfigError> reloadAll() {
        List<ConfigManager.ConfigError> errors = configs.load(true);
        if (!errors.isEmpty()) return errors;
        return applyConfig();
    }

    /** Aplica la configuración cargada a cada módulo. Un fallo en un módulo no afecta a los demás. */
    public List<ConfigManager.ConfigError> applyConfig() {
        List<ConfigManager.ConfigError> errors = new ArrayList<>();
        run(errors, "modules.yml", modules::reload);
        run(errors, "config.yml", sounds::reload);
        run(errors, "config.yml", this::scheduleAutosave);
        run(errors, "config.yml", days::reload);
        run(errors, "difficulty.yml", difficulty::reload);
        run(errors, "pvp.yml", pvp::reload);
        run(errors, "lives.yml", lives::reload);
        run(errors, "actionbar.yml", actionBar::reload);
        run(errors, "mobs.yml", mobs::reload);
        run(errors, "mobs.yml", spiders::reload);
        run(errors, "mobs.yml", effects::reload);
        run(errors, "nightmare.yml", nightmare::reload);
        run(errors, "nightmare.yml", perf::reload);
        run(errors, "nightmare.yml", spawns::reload);
        run(errors, "nightmare.yml", multiplication::reload);
        run(errors, "nightmare.yml", events::reload);
        run(errors, "nightmare.yml", weather::reload);
        run(errors, "nightmare.yml", zones::reload);
        run(errors, "nightmare.yml", worldListener::reload);
        run(errors, "revenge.yml", revenge::reload);
        return errors;
    }

    private void run(List<ConfigManager.ConfigError> errors, String file, Runnable r) {
        try {
            r.run();
        } catch (Exception ex) {
            errors.add(new ConfigManager.ConfigError(file, -1, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            getLogger().log(Level.WARNING, "Error aplicando " + file, ex);
        }
    }

    private void scheduleAutosave() {
        if (autosaveTask != null) autosaveTask.cancel();
        long ms = TimeUtil.parse(configs.get("config.yml").getString("storage.autosave-interval"), 300_000L);
        long ticks = Math.max(200L, ms / 50L);
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> lives.saveIfDirty(), ticks, ticks);
    }

    /** Muestra el resultado de una recarga a quien la pidió. */
    public void reportReload(CommandSender to, List<ConfigManager.ConfigError> errors) {
        if (errors.isEmpty()) {
            messages.send(to, "reload-ok");
            return;
        }
        for (ConfigManager.ConfigError e : errors) {
            if (e.line() > 0) messages.send(to, "reload-error", "file", e.file(), "line", e.line());
            else messages.send(to, "reload-error-noline", "file", e.file(), "error", e.message());
        }
        messages.send(to, "reload-kept-old");
    }

    // ------------------------------------------------------------------
    //  Accesos
    // ------------------------------------------------------------------

    public DataFile io() { return io; }
    public ConfigManager configs() { return configs; }
    public Messages messages() { return messages; }
    public SoundManager sounds() { return sounds; }
    public StateStore state() { return state; }
    public DayManager days() { return days; }
    public DifficultyManager difficulty() { return difficulty; }
    public PVPManager pvp() { return pvp; }
    public LivesManager lives() { return lives; }
    public ActionBarManager actionBar() { return actionBar; }
    public MobManager mobs() { return mobs; }
    public SpiderManager spiders() { return spiders; }
    public MobEffectsManager effects() { return effects; }
    public ModuleManager modules() { return modules; }
    public NightmareManager nightmare() { return nightmare; }
    public PerformanceManager perf() { return perf; }
    public SpawnManager spawns() { return spawns; }
    public HordeManager hordes() { return hordes; }
    public NightmareMobManager nmMobs() { return nmMobs; }
    public NightmareAI ai() { return ai; }
    public MultiplicationManager multiplication() { return multiplication; }
    public EventManager events() { return events; }
    public BossManager bosses() { return bosses; }
    public WeatherManager weather() { return weather; }
    public AcidRainManager acidRain() { return acidRain; }
    public PlayerManager playerManager() { return playerManager; }
    public CursedZoneManager zones() { return zones; }
    public VengeanceManager vengeance() { return vengeance; }
    public RevengeManager revenge() { return revenge; }
}
