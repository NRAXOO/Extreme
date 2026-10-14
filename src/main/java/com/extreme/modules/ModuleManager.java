package com.extreme.modules;

import com.extreme.ExtremePlugin;
import com.extreme.gui.ActionBarMenu;
import com.extreme.gui.LivesMenu;
import com.extreme.gui.Menu;
import com.extreme.gui.PVPMenu;
import com.extreme.gui.WorldBossMenu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Panel de MÓDULOS: cada sistema del plugin tiene su propio interruptor (activar/desactivar) y su lista de
 * ajustes editables desde el panel. Los módulos nuevos viven en modules.yml; los antiguos (vidas, ActionBar,
 * dificultad, PVP aleatorio, venganza) conservan su interruptor de siempre pero se muestran aquí también.
 */
public class ModuleManager {

    public enum Category { BASE, DIFFICULTY, MOBS, NIGHTMARE, REVENGE }

    public enum Type { DOUBLE, INT, BOOL, TIME }

    /** Un ajuste editable desde el panel (se guarda en el archivo YAML indicado). */
    public record Setting(String file, String path, String label, Type type, double step, double min, double max) {}

    public static final class Feature {
        public final String key;
        public final Category category;
        public final Material icon;
        public final List<Setting> settings;
        public final Function<ExtremePlugin, Menu> detail;
        public final boolean locked;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;

        Feature(String key, Category category, Material icon, Supplier<Boolean> getter, Consumer<Boolean> setter,
                List<Setting> settings, Function<ExtremePlugin, Menu> detail, boolean locked) {
            this.key = key;
            this.category = category;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
            this.settings = settings;
            this.detail = detail;
            this.locked = locked;
        }
    }

    private static final String NM = "nightmare.yml", MOB = "mobs.yml";

    private final ExtremePlugin plugin;
    private final Map<String, Feature> features = new LinkedHashMap<>();
    private final Map<String, Boolean> cache = new HashMap<>();

    public ModuleManager(ExtremePlugin plugin) {
        this.plugin = plugin;
        registerAll();
    }

    // ------------------------------------------------------------------
    //  API
    // ------------------------------------------------------------------

    public void reload() {
        for (Feature f : features.values()) {
            if (isFileBacked(f)) cache.put(f.key, plugin.configs().get("modules.yml").getBoolean("modules." + f.key, false));
        }
    }

    private boolean isFileBacked(Feature f) {
        return !LEGACY.contains(f.key) && !f.locked;
    }

    /** Estado de un módulo. Para los de modules.yml es una consulta a un mapa (barata en eventos frecuentes). */
    public boolean on(String key) {
        Boolean c = cache.get(key);
        if (c != null) return c;
        Feature f = features.get(key);
        return f != null && f.getter.get();
    }

    public Feature get(String key) { return features.get(key); }

    public List<Feature> byCategory(Category c) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features.values()) if (f.category == c) out.add(f);
        return out;
    }

    public Iterable<Feature> all() { return features.values(); }

    public int countOn(Category c) {
        int n = 0;
        for (Feature f : byCategory(c)) if (!f.locked && on(f.key)) n++;
        return n;
    }

    public int countTotal(Category c) {
        int n = 0;
        for (Feature f : byCategory(c)) if (!f.locked) n++;
        return n;
    }

    /** Activa/desactiva un módulo y aplica el cambio al instante (sin reiniciar). */
    public boolean set(String key, boolean value) {
        Feature f = features.get(key);
        if (f == null || f.locked) return false;
        f.setter.accept(value);
        plugin.applyConfig();
        return true;
    }

    // ------------------------------------------------------------------
    //  Definición de módulos
    // ------------------------------------------------------------------

    private static final List<String> LEGACY = List.of("pvp_random", "lives", "actionbar", "difficulty", "revenge");

    private static Setting d(String file, String path, String label, double step, double min, double max) {
        return new Setting(file, path, label, Type.DOUBLE, step, min, max);
    }

    private static Setting i(String file, String path, String label, double step, double min, double max) {
        return new Setting(file, path, label, Type.INT, step, min, max);
    }

    private static Setting b(String file, String path, String label) {
        return new Setting(file, path, label, Type.BOOL, 1, 0, 1);
    }

    private static Setting t(String file, String path, String label, double stepMs, double minMs) {
        return new Setting(file, path, label, Type.TIME, stepMs, minMs, 7 * 86_400_000.0);
    }

    private void legacy(String key, Category cat, Material icon, Supplier<Boolean> g, Consumer<Boolean> s,
                        Function<ExtremePlugin, Menu> detail, Setting... settings) {
        features.put(key, new Feature(key, cat, icon, g, s, List.of(settings), detail, false));
    }

    private void file(String key, Category cat, Material icon, Setting... settings) {
        features.put(key, new Feature(key, cat, icon,
                () -> plugin.configs().get("modules.yml").getBoolean("modules." + key, false),
                v -> {
                    plugin.configs().set("modules.yml", "modules." + key, v);
                    cache.put(key, v);
                }, List.of(settings), null, false));
    }

    private void fileDetail(String key, Category cat, Material icon, Function<ExtremePlugin, Menu> detail, Setting... settings) {
        features.put(key, new Feature(key, cat, icon,
                () -> plugin.configs().get("modules.yml").getBoolean("modules." + key, false),
                v -> {
                    plugin.configs().set("modules.yml", "modules." + key, v);
                    cache.put(key, v);
                }, List.of(settings), detail, false));
    }

    private void locked(String key, Category cat, Material icon, Setting... settings) {
        features.put(key, new Feature(key, cat, icon, () -> true, v -> { }, List.of(settings), null, true));
    }

    private void registerAll() {
        // ---------------- SISTEMA BASE ----------------
        legacy("pvp_random", Category.BASE, Material.CLOCK, () -> plugin.pvp().isRandomEnabled(),
                v -> plugin.pvp().setRandom(v), PVPMenu::new,
                b("pvp.yml", "pvp.random.manual-resets-timer", "Reiniciar temporizador si es manual"));
        legacy("lives", Category.BASE, Material.TOTEM_OF_UNDYING, () -> plugin.lives().isEnabled(),
                v -> plugin.lives().setEnabled(v), LivesMenu::new);
        legacy("actionbar", Category.BASE, Material.NAME_TAG,
                () -> plugin.configs().get("actionbar.yml").getBoolean("actionbar.enabled", true),
                v -> {
                    plugin.configs().set("actionbar.yml", "actionbar.enabled", v);
                    plugin.actionBar().reload();
                }, ActionBarMenu::new);

        // ---------------- DIFICULTAD MANUAL ----------------
        legacy("difficulty", Category.DIFFICULTY, Material.WITHER_SKELETON_SKULL, () -> plugin.difficulty().isEnabled(),
                v -> plugin.difficulty().setEnabled(v), null,
                d("difficulty.yml", "difficulty.manual.mob-damage", "Daño de mobs (x)", 0.05, 0.1, 10),
                d("difficulty.yml", "difficulty.manual.mob-health", "Vida de mobs (x)", 0.05, 0.1, 10),
                d("difficulty.yml", "difficulty.manual.mob-speed", "Velocidad de mobs (x)", 0.01, 0.5, 3),
                d("difficulty.yml", "difficulty.manual.mob-follow-range", "Rango de persecución (x)", 0.05, 0.5, 5),
                d("difficulty.yml", "difficulty.manual.spider-cobweb-chance", "Cobwebs de arañas % (-1 = base)", 5, -1, 100),
                d("difficulty.yml", "difficulty.manual.effect-chance-bonus", "Bonus % de efectos", 5, 0, 100),
                d("difficulty.yml", "difficulty.manual.effect-duration-multiplier", "Duración de efectos (x)", 0.1, 0.1, 10),
                d("difficulty.yml", "difficulty.manual.buff-chance", "Mobs con buffs %", 5, 0, 100),
                i("difficulty.yml", "difficulty.manual.buff-amplifier", "Nivel de buffs", 1, 0, 4),
                b("difficulty.yml", "difficulty.notify-on-join", "Avisar al entrar"));

        // ---------------- MOBS ----------------
        file("mob_stats", Category.MOBS, Material.ZOMBIE_HEAD,
                d(MOB, "mobs.global-damage-multiplier", "Daño global (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.global-health-multiplier", "Vida global (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.zombie.damage-multiplier", "Zombie: daño (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.zombie.health-multiplier", "Zombie: vida (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.skeleton.damage-multiplier", "Esqueleto: daño (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.skeleton.health-multiplier", "Esqueleto: vida (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.spider.damage-multiplier", "Araña: daño (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.spider.health-multiplier", "Araña: vida (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.creeper.damage-multiplier", "Creeper: daño (x)", 0.05, 0.1, 10),
                d(MOB, "mobs.creeper.health-multiplier", "Creeper: vida (x)", 0.05, 0.1, 10));
        file("spider_cobweb", Category.MOBS, Material.COBWEB,
                d(MOB, "spider.cobweb.chance", "Probabilidad %", 5, 0, 100),
                d(MOB, "spider.cobweb.cooldown", "Cooldown (s)", 1, 0, 60),
                i(MOB, "spider.cobweb.radius", "Radio", 1, 0, 6),
                i(MOB, "spider.cobweb.max-webs-per-use", "Cobwebs por uso", 1, 1, 20),
                d(MOB, "spider.cobweb.duration", "Duración (s)", 1, 1, 60),
                i(MOB, "spider.cobweb.max-active-webs", "Máximo activas", 10, 10, 500),
                i(MOB, "spider.cobweb.minimum-day", "Día mínimo", 1, 1, 60));
        file("mob_effects", Category.MOBS, Material.SPIDER_EYE,
                d(MOB, "mob-effects.spider.poison.chance", "Araña: veneno %", 5, 0, 100),
                d(MOB, "mob-effects.spider.poison.duration", "Araña: veneno (s)", 1, 1, 180),
                d(MOB, "mob-effects.cave_spider.poison.chance", "Araña cueva: veneno %", 5, 0, 100),
                d(MOB, "mob-effects.zombie.hunger.chance", "Zombie: hambre %", 5, 0, 100),
                d(MOB, "mob-effects.skeleton.slowness.chance", "Esqueleto: lentitud %", 5, 0, 100),
                d(MOB, "mob-effects.wither_skeleton.wither.chance", "Esq. Wither: wither %", 5, 0, 100),
                i(MOB, "mob-effects.spider.poison.minimum-day", "Veneno: día mínimo", 1, 1, 60));

        // ---------------- MODO PESADILLA ----------------
        file("nightmare", Category.NIGHTMARE, Material.WITHER_SKELETON_SKULL,
                i(NM, "nightmare.max-level", "Nivel máximo", 1, 1, 500),
                i(NM, "nightmare.days-per-level", "Días por nivel (modo LINEAR)", 1, 1, 60),
                d(NM, "nightmare.scaling.health.per-level", "Vida por nivel", 0.01, 0, 1),
                d(NM, "nightmare.scaling.damage.per-level", "Daño por nivel", 0.01, 0, 1),
                d(NM, "nightmare.scaling.speed.per-level", "Velocidad por nivel", 0.005, 0, 0.2),
                d(NM, "nightmare.scaling.spawn-rate.per-level", "Spawn por nivel", 0.02, 0, 1),
                d(NM, "nightmare.scaling.mob-count.per-level", "Cantidad por nivel", 0.02, 0, 1),
                d(NM, "nightmare.scaling.event-frequency.per-level", "Eventos por nivel", 0.02, 0, 1),
                d(NM, "nightmare.scaling.elite-chance.per-level", "Élites por nivel", 0.005, 0, 0.2),
                d(NM, "nightmare.scaling.acid-intensity.per-level", "Lluvia ácida por nivel", 0.05, 0, 1),
                d(NM, "nightmare.scaling.event-duration.per-level", "Duración eventos por nivel", 0.01, 0, 1),
                b(NM, "nightmare.uncapped-days", "Días sin tope de max-days"));
        file("nm_spawn", Category.NIGHTMARE, Material.SPAWNER,
                d(NM, "spawn.chance", "Probabilidad por ciclo", 0.05, 0, 1),
                d(NM, "spawn.count", "Mobs por tanda", 1, 1, 20),
                d(NM, "spawn.cooldown", "Cooldown (s)", 1, 1, 120),
                d(NM, "spawn.min-distance", "Distancia mínima", 1, 4, 60),
                d(NM, "spawn.max-distance", "Distancia máxima", 2, 8, 100),
                i(NM, "spawn.cycle-ticks", "Ciclo (ticks)", 20, 20, 1200),
                d(NM, "spawn.natural-extra", "Extra a spawns naturales", 0.25, 0, 5),
                b(NM, "spawn.allow-daytime", "Permitir de día"));
        file("nm_buffs", Category.NIGHTMARE, Material.POTION,
                i(NM, "mobs.strength", "Fuerza (nivel)", 1, 0, 5),
                i(NM, "mobs.speed", "Velocidad (nivel)", 1, 0, 5),
                i(NM, "mobs.strength-upgrade-level", "Fuerza +1 desde nivel", 1, 1, 100),
                i(NM, "mobs.speed-upgrade-level", "Velocidad +1 desde nivel", 1, 1, 100),
                i(NM, "mobs.resistance-from-level", "Resistencia desde nivel", 1, 1, 100),
                i(NM, "mobs.regeneration-from-level", "Regeneración desde nivel", 1, 1, 100));
        file("nm_detection", Category.NIGHTMARE, Material.ENDER_EYE,
                d(NM, "mobs.detection-range", "Detección base (bloques)", 1, 8, 64),
                d(NM, "nightmare.detection.per-level", "Detección extra por nivel", 0.5, 0, 5),
                d(NM, "nightmare.detection.max-extra", "Detección extra máxima", 2, 0, 64),
                d(NM, "ai.memory-seconds", "Memoria de posición (s)", 5, 0, 120),
                d(NM, "ai.low-health-priority", "Prioridad a poca vida", 1, 0, 40));
        file("nm_elite", Category.NIGHTMARE, Material.GOLDEN_HELMET,
                d(NM, "elite.health-bonus", "Vida extra (+x)", 0.1, 0, 10),
                d(NM, "elite.damage-bonus", "Daño extra (+x)", 0.1, 0, 10),
                d(NM, "elite.speed-bonus", "Velocidad extra (+x)", 0.05, 0, 2),
                d(NM, "elite.xp-multiplier", "XP (x)", 0.5, 1, 20),
                b(NM, "elite.glowing", "Brillo"));
        file("nm_zombies", Category.NIGHTMARE, Material.ZOMBIE_HEAD,
                d(NM, "zombies.summoner-chance", "Invocador %", 0.01, 0, 1),
                d(NM, "zombies.mini-zombie-chance", "Mini zombie %", 0.01, 0, 1),
                d(NM, "zombies.armor-chance", "Armadura %", 0.05, 0, 1),
                d(NM, "zombies.weapon-chance", "Arma %", 0.05, 0, 1),
                d(NM, "zombies.enchant-chance", "Encantado %", 0.05, 0, 1),
                i(NM, "zombies.summoner-total", "Horda del invocador", 1, 1, 60),
                d(NM, "zombies.summoner-cooldown", "Cooldown invocador (s)", 5, 5, 300),
                b(NM, "zombies.break-doors", "Rompen puertas"),
                b(NM, "zombies.pick-up-items", "Recogen equipo"));
        file("nm_spiders", Category.NIGHTMARE, Material.FERMENTED_SPIDER_EYE,
                d(NM, "spiders.venom-chance", "Araña venenosa %", 0.01, 0, 1),
                d(NM, "spiders.poison-chance", "Veneno al golpear %", 0.05, 0, 1),
                i(NM, "spiders.poison-duration", "Veneno (s)", 5, 1, 180),
                i(NM, "poison.max-duration", "Veneno máximo (s)", 10, 10, 600),
                d(NM, "spawn.spider-ceiling-chance", "Aparecer en el techo %", 0.05, 0, 1));
        file("nm_endermen", Category.NIGHTMARE, Material.ENDER_PEARL,
                d(NM, "enderman.teleport-chance", "Teletransporte %", 0.05, 0, 1),
                d(NM, "enderman.behind-chance", "Aparecer detrás %", 0.05, 0, 1),
                d(NM, "enderman.teleport-cooldown", "Cooldown (s)", 1, 1, 60));
        file("nm_creepers", Category.NIGHTMARE, Material.CREEPER_HEAD,
                d(NM, "creepers.explosion-radius", "Radio de explosión (3 = vanilla)", 0.5, 0.5, 30),
                b(NM, "creepers.explosion-break-blocks", "Explosión destruye bloques"),
                b(NM, "creepers.explosion-fire", "Explosión provoca fuego"),
                d(NM, "creepers.big-radius", "Variante grande: radio", 0.5, 1, 30),
                d(NM, "creepers.giant-radius", "Variante gigante: radio", 0.5, 1, 30),
                d(NM, "bosses.creeper_king.explosion-radius", "Creeper King: radio", 1, 3, 100),
                b(NM, "bosses.creeper_king.explosion-break-blocks", "Creeper King: destruye bloques"),
                b(NM, "bosses.creeper_king.explosion-fire", "Creeper King: fuego"),
                d(NM, "creepers.charged-chance", "Eléctrico % base", 0.01, 0, 1),
                d(NM, "creepers.charged-per-level", "Eléctrico + por nivel", 0.005, 0, 0.1),
                d(NM, "creepers.charged-max", "Eléctrico máximo", 0.05, 0, 1),
                d(NM, "creepers.variants.fast", "Variante rápido %", 0.01, 0, 0.3),
                d(NM, "creepers.variants.invisible", "Variante invisible %", 0.01, 0, 0.3),
                d(NM, "creepers.variants.giant", "Variante gigante %", 0.01, 0, 0.3),
                d(NM, "creepers.variants.fire", "Variante incendiario %", 0.01, 0, 0.3),
                d(NM, "creepers.variants.big", "Variante explosión grande %", 0.01, 0, 0.3),
                d(NM, "creepers.variants.brood", "Variante madre %", 0.01, 0, 0.3));
        file("nm_skeletons", Category.NIGHTMARE, Material.BOW,
                d(NM, "skeletons.accuracy", "Precisión", 0.01, 0.1, 1),
                d(NM, "skeletons.sniper-chance", "Sniper %", 0.01, 0, 1),
                d(NM, "skeletons.sniper-accuracy", "Precisión sniper", 0.005, 0.1, 1),
                d(NM, "skeletons.burst-chance", "Ráfaga %", 0.05, 0, 1),
                d(NM, "skeletons.arrows.poison", "Flecha veneno %", 0.01, 0, 0.5),
                d(NM, "skeletons.arrows.slowness", "Flecha lentitud %", 0.01, 0, 0.5),
                d(NM, "skeletons.arrows.weakness", "Flecha debilidad %", 0.01, 0, 0.5),
                d(NM, "skeletons.arrows.explosive", "Flecha explosiva %", 0.01, 0, 0.5),
                b(NM, "skeletons.arrow-break-blocks", "Explosiones rompen bloques"));
        file("nm_golems", Category.NIGHTMARE, Material.IRON_BLOCK,
                d(NM, "golems.health-multiplier", "Vida (x)", 0.25, 1, 20),
                d(NM, "golems.damage-multiplier", "Daño (x)", 0.25, 1, 20),
                d(NM, "golems.devastating-chance", "Golpe devastador %", 0.05, 0, 1),
                d(NM, "golems.devastating-damage", "Daño devastador", 100, 10, 5000),
                d(NM, "golems.angry-seconds", "Enfado (s)", 5, 5, 300),
                d(NM, "golems.elite-chance", "Golem élite %", 0.05, 0, 1),
                b(NM, "golems.enraged-on-hit", "Se enfurecen al ser golpeados"));
        file("nm_ghast", Category.NIGHTMARE, Material.GHAST_TEAR,
                d(NM, "ghast.demonic-chance", "Ghast naturales demoníacos %", 0.05, 0, 1),
                d(NM, "ghast.nether-spawn-chance", "Aparición propia %", 0.05, 0, 1),
                d(NM, "ghast.nether-cooldown", "Cooldown aparición (s)", 5, 1, 300),
                i(NM, "ghast.max-per-player", "Máximo por jugador", 1, 1, 10),
                d(NM, "ghast.health", "Vida (normal = 10)", 5, 10, 500),
                d(NM, "ghast.scale", "Tamaño (x)", 0.1, 0.5, 4),
                d(NM, "ghast.explosion-power", "Potencia de explosión", 0.5, 0.5, 30),
                i(NM, "ghast.extra-fireballs", "Bolas extra por disparo", 1, 0, 6),
                b(NM, "ghast.incendiary", "Provoca fuego"),
                b(NM, "ghast.break-blocks", "Destruye bloques"));
        file("nm_piglins", Category.NIGHTMARE, Material.GOLD_INGOT,
                d(NM, "piglins.detection-range", "Detección (bloques)", 1, 8, 64),
                d(NM, "piglins.chase-speed", "Velocidad de persecución", 0.1, 0.5, 3),
                d(NM, "piglins.damage", "Daño de respaldo", 0.5, 0.5, 30),
                d(NM, "piglins.attack-cooldown", "Cooldown de ataque (s)", 0.25, 0.25, 10));
        file("nm_animals", Category.NIGHTMARE, Material.LEATHER,
                d(NM, "nightmare.scaling.animal-hostile-chance.base", "Hostiles % base", 0.02, 0, 1),
                d(NM, "nightmare.scaling.animal-hostile-chance.per-level", "Hostiles + por nivel", 0.01, 0, 0.2),
                d(NM, "animals.damage", "Daño", 0.5, 0.5, 40),
                d(NM, "animals.detection-range", "Detección", 1, 4, 48),
                i(NM, "animals.strength", "Fuerza (nivel)", 1, 0, 5),
                i(NM, "animals.speed", "Velocidad (nivel)", 1, 0, 5));
        file("nm_chicken", Category.NIGHTMARE, Material.FEATHER,
                d(NM, "chicken.explosive-chance", "Pollo explosivo %", 0.02, 0, 1),
                i(NM, "chicken.speed-level", "Velocidad (nivel)", 1, 0, 5),
                d(NM, "chicken.trigger-distance", "Distancia de activación", 0.5, 1, 15),
                d(NM, "chicken.countdown", "Cuenta atrás (s)", 0.5, 0.5, 10),
                d(NM, "chicken.power", "Potencia", 0.2, 0.5, 8),
                b(NM, "chicken.break-blocks", "Rompe bloques"));
        file("nm_wardens", Category.NIGHTMARE, Material.SCULK_SHRIEKER,
                d(NM, "wardens.chance", "Probabilidad", 0.001, 0, 0.1),
                d(NM, "wardens.mutated-chance", "Mutado %", 0.05, 0, 1),
                i(NM, "wardens.max-per-world", "Máximo por mundo", 1, 0, 10),
                b(NM, "wardens.normal-cave-spawn", "En cuevas normales"));
        file("nm_multiplication", Category.NIGHTMARE, Material.SLIME_BALL,
                d(NM, "multiplication.zombie.chance", "Zombie %", 0.02, 0, 1),
                d(NM, "multiplication.mini_zombie.chance", "Mini zombie %", 0.02, 0, 1),
                d(NM, "multiplication.spider.chance", "Araña %", 0.02, 0, 1),
                d(NM, "multiplication.enderman.chance", "Enderman %", 0.02, 0, 1),
                d(NM, "multiplication.creeper.chance", "Creeper %", 0.02, 0, 1),
                i(NM, "multiplication.zombie.max-generations", "Zombie: generaciones", 1, 1, 8),
                i(NM, "multiplication.mini_zombie.max-generations", "Mini zombie: generaciones", 1, 1, 8));
        file("nm_bosses", Category.NIGHTMARE, Material.DRAGON_HEAD,
                d(NM, "bosses.level-scaling", "Fuerza por nivel", 0.01, 0, 0.5),
                d(NM, "nightmare.scaling.miniboss-chance.base", "Mini-jefe % base", 0.0005, 0, 0.1),
                d(NM, "nightmare.scaling.miniboss-chance.per-level", "Mini-jefe + por nivel", 0.0002, 0, 0.01));
        fileDetail("nm_world_boss", Category.NIGHTMARE, Material.NETHER_STAR, WorldBossMenu::new,
                i(NM, "world-boss.first-day", "Primer jefe (día)", 1, 1, 200),
                i(NM, "world-boss.every-days", "Cada N días", 1, 1, 100),
                i(NM, "world-boss.announce-seconds", "Cuenta atrás (s)", 30, 10, 3600),
                i(NM, "world-boss.prelude-seconds", "Preludio (s)", 1, 3, 60),
                d(NM, "world-boss.multiplier", "Fuerza extra (x)", 0.25, 1, 10),
                i(NM, "world-boss.default-radius", "Radio de punto nuevo", 10, 5, 500),
                b(NM, "world-boss.show-coordinates", "Anunciar coordenadas"),
                b(NM, "world-boss.use-points", "Usar puntos de aparición"),
                b(NM, "world-boss.surface-only", "Aparecer en la superficie"),
                b(NM, "world-boss.random-fallback", "Aleatorio si no hay puntos"));
        file("nm_events", Category.NIGHTMARE, Material.BELL,
                d(NM, "events.chance-per-check", "Probabilidad por sorteo", 0.05, 0, 1),
                i(NM, "events.check-interval", "Sorteo cada (s)", 5, 10, 600),
                i(NM, "events.global-cooldown", "Cooldown global (s)", 30, 0, 7200),
                i(NM, "events.max-concurrent", "Eventos simultáneos", 1, 1, 6),
                i(NM, "events.max-per-player", "Eventos por jugador", 1, 1, 4));
        file("nm_acid_rain", Category.NIGHTMARE, Material.WATER_BUCKET,
                d(NM, "acid-rain.damage", "Daño", 0.5, 0.5, 20),
                d(NM, "acid-rain.max-damage", "Daño máximo", 0.5, 1, 40),
                i(NM, "acid-rain.max-duration", "Duración máxima (s)", 10, 10, 900),
                d(NM, "weather.acid-chance", "Tormenta ácida %", 0.05, 0, 1),
                b(NM, "acid-rain.helmet-reduces", "El casco reduce el daño"));
        file("nm_weather", Category.NIGHTMARE, Material.LIGHTNING_ROD,
                d(NM, "weather.storm-chance", "Tormenta %", 0.01, 0, 1),
                d(NM, "weather.thunder-chance", "Tormenta eléctrica %", 0.05, 0, 1),
                d(NM, "weather.lightning-chance", "Rayos cerca %", 0.02, 0, 1),
                d(NM, "weather.lightning-event-chance", "Rayo invoca mobs %", 0.05, 0, 1),
                i(NM, "weather.min-duration", "Duración mínima (s)", 30, 30, 3600),
                i(NM, "weather.max-duration", "Duración máxima (s)", 30, 60, 7200),
                b(NM, "weather.lightning-near-players", "Rayos cerca de jugadores"),
                b(NM, "weather.snow-slowness", "Nieve ralentizadora"));
        file("nm_player", Category.NIGHTMARE, Material.COOKED_BEEF,
                d(NM, "player.regen-multiplier", "Regeneración (x)", 0.1, 0, 2),
                d(NM, "player.food-effectiveness", "Comida (x)", 0.1, 0.1, 2),
                d(NM, "player.saturation-multiplier", "Saturación (x)", 0.1, 0, 2),
                d(NM, "player.starvation-multiplier", "Daño por hambre (x)", 0.25, 1, 10),
                d(NM, "player.armor-durability-multiplier", "Desgaste armadura (x)", 0.25, 1, 10),
                d(NM, "player.tool-durability-multiplier", "Desgaste herramientas (x)", 0.25, 1, 10),
                d(NM, "player.shield-break-chance", "Romper escudo %", 0.05, 0, 1),
                d(NM, "player.xp-loss-percent", "XP perdida al morir", 0.1, 0, 1));
        file("nm_threat", Category.NIGHTMARE, Material.TARGET,
                d(NM, "threat.half-life-minutes", "Decaimiento (min)", 1, 1, 120),
                d(NM, "threat.bonus-per-point", "Bonus por punto", 0.005, 0, 0.1),
                d(NM, "threat.max-bonus", "Bonus máximo", 0.1, 0, 5));
        file("nm_sleep", Category.NIGHTMARE, Material.RED_BED,
                d(NM, "sleep.fail-chance", "Fallar al dormir %", 0.05, 0, 1),
                d(NM, "sleep.ambush-chance", "Emboscada %", 0.05, 0, 1),
                d(NM, "sleep.long-night-chance", "Noche larga %", 0.05, 0, 1),
                i(NM, "sleep.ambush-mobs", "Mobs de emboscada", 1, 1, 40),
                i(NM, "sleep.long-night-lock-seconds", "Bloqueo de cama (s)", 10, 10, 900));
        file("nm_structures", Category.NIGHTMARE, Material.CHEST,
                d(NM, "structures.chest-traps.chance", "Cofre trampa %", 0.05, 0, 1),
                i(NM, "structures.cooldown", "Cooldown (s)", 30, 30, 7200),
                i(NM, "structures.village.min-villagers", "Aldea: aldeanos mínimos", 1, 1, 20),
                b(NM, "structures.chest-traps.enabled", "Cofres trampa"),
                b(NM, "structures.ancient-city.enabled", "Ancient City"),
                b(NM, "structures.fortress.enabled", "Fortaleza del Nether"),
                b(NM, "structures.village.enabled", "Invasión de aldeas"));
        file("nm_zones", Category.NIGHTMARE, Material.SCULK,
                d(NM, "cursed-zones.spawn-multiplier", "Spawn (x)", 0.25, 1, 10),
                d(NM, "cursed-zones.boss-multiplier", "Mini-jefes (x)", 0.5, 1, 20),
                b(NM, "cursed-zones.darkness", "Oscuridad"),
                b(NM, "cursed-zones.acid-rain", "Lluvia ácida"));
        file("nm_fury", Category.NIGHTMARE, Material.BLAZE_POWDER,
                i(NM, "vengeance.kills-threshold", "Muertes para activar", 1, 3, 100),
                i(NM, "vengeance.window-seconds", "Ventana (s)", 5, 5, 300),
                i(NM, "vengeance.duration-seconds", "Duración (s)", 10, 10, 1800),
                d(NM, "vengeance.spawn-multiplier", "Spawn (x)", 0.25, 1, 10),
                d(NM, "vengeance.horde-chance", "Horda %", 0.05, 0, 1),
                b(NM, "vengeance.on-death.enabled", "Modo furia tras morir"));
        locked("performance", Category.NIGHTMARE, Material.REDSTONE_TORCH,
                i(NM, "performance.max-mobs-global", "Mobs globales", 10, 10, 2000),
                i(NM, "performance.max-mobs-per-player", "Mobs por jugador", 5, 1, 300),
                i(NM, "performance.max-event-mobs", "Mobs por evento", 10, 5, 500),
                i(NM, "performance.max-bosses", "Jefes simultáneos", 1, 0, 10),
                i(NM, "performance.max-explosions-per-second", "Explosiones por segundo", 1, 1, 30),
                i(NM, "hordes.max-per-player", "Hordas por jugador", 1, 1, 5),
                i(NM, "ai.max-tracked", "Mobs con IA especial", 10, 10, 1000));

        // ---------------- VENGANZA (SOLO JUGADOR VS JUGADOR) ----------------
        legacy("revenge", Category.REVENGE, Material.IRON_SWORD,
                () -> plugin.configs().get("revenge.yml").getBoolean("revenge.enabled", true),
                v -> plugin.configs().set("revenge.yml", "revenge.enabled", v), null,
                t("revenge.yml", "revenge.duration", "Tiempo para vengarse", 60_000, 60_000),
                i("revenge.yml", "revenge.steal-lives", "Corazones robados", 1, 1, 10),
                b("revenge.yml", "revenge.can-eliminate-target", "Puede eliminar al asesino"),
                b("revenge.yml", "revenge.respect-maximum", "Respetar vidas máximas"),
                b("revenge.yml", "revenge.warn-killer", "Avisar al asesino"),
                b("revenge.yml", "revenge.broadcast", "Anunciar en el chat"),
                b("revenge.yml", "revenge.glow-killer", "Asesino brilla"),
                b("revenge.yml", "revenge.revenge-kill-creates-revenge", "Cadena de venganzas"));
    }
}
