package com.extreme.commands;

import com.extreme.ExtremePlugin;
import com.extreme.nightmare.BossManager;
import com.extreme.nightmare.CursedZoneManager;
import com.extreme.utils.Text;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /pesadilla  (solo lo esencial, todo en español). El resto se configura desde /panel. */
public class NightmareCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> EVENTOS = new LinkedHashMap<>();
    private static final Map<String, String> JEFES = new LinkedHashMap<>();

    static {
        EVENTOS.put("zombies", "zombie_wave");
        EVENTOS.put("aranas", "spider_wave");
        EVENTOS.put("endermen", "endermen_night");
        EVENTOS.put("creepers", "creeper_storm");
        EVENTOS.put("esqueletos", "skeleton_invasion");
        EVENTOS.put("apocalipsis", "apocalypse");
        EVENTOS.put("acida", "acid_rain");
        EVENTOS.put("lluvia", "mob_rain");
        EVENTOS.put("oscuridad", "darkness");
        EVENTOS.put("sangre", "blood");
        EVENTOS.put("invasion", "world_invasion");
        JEFES.put("zombie", "zombie_king");
        JEFES.put("arana", "spider_queen");
        JEFES.put("creeper", "creeper_king");
        JEFES.put("esqueleto", "skeleton_sniper");
        JEFES.put("ender", "ender_beast");
        JEFES.put("warden", "mutated_warden");
    }

    private final ExtremePlugin plugin;

    public NightmareCommand(ExtremePlugin plugin) {
        this.plugin = plugin;
    }

    private void say(CommandSender s, String text) {
        s.sendMessage(Text.color(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!CommandUtil.has(sender, "extreme.nightmare")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        String sub = a.length == 0 ? "ayuda" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "nivel" -> nivel(sender, a);
            case "evento" -> evento(sender, a);
            case "jefe" -> jefe(sender, a);
            case "zona" -> zona(sender, a);
            case "invocar" -> invocar(sender, a);
            case "limpiar" -> say(sender, "&aSe eliminaron &f" + plugin.perf().clearAll() + " &amobs de la Pesadilla.");
            case "estado" -> estado(sender);
            default -> ayuda(sender);
        }
        return true;
    }

    private void estado(CommandSender s) {
        for (String l : plugin.nightmare().statusLines()) say(s, l);
        say(s, "&7Mobs: &f" + plugin.perf().total() + "&8/&f" + plugin.perf().maxGlobal() + " &8| &7Hordas: &f" + plugin.hordes().activeCount()
                + " &8| &7Jefes: &f" + plugin.bosses().count() + "&8/&f" + plugin.perf().maxBosses()
                + " &8| &7Eventos: &f" + plugin.events().activeCount());
        if (plugin.bosses().isCountdownActive()) {
            say(s, "&4☠ Jefe mundial en &f" + TimeUtil.clock(plugin.bosses().countdownRemainingMs()) + " &7- &c" + plugin.bosses().countdownCoords());
        } else {
            long next = plugin.bosses().nextScheduledMs();
            say(s, "&7Próximo jefe mundial programado: &f" + (next < 0 ? "desactivado" : TimeUtil.format(next)));
        }
    }

    private void nivel(CommandSender s, String[] a) {
        if (a.length >= 3 && a[1].equalsIgnoreCase("fijar")) {
            if (a[2].equalsIgnoreCase("auto")) {
                plugin.nightmare().setOverride(0);
            } else {
                Integer n = CommandUtil.parseInt(a[2]);
                if (n == null || n < 1) {
                    say(s, "&cNivel inválido.");
                    return;
                }
                plugin.nightmare().setOverride(n);
            }
        }
        say(s, "&7Nivel de Pesadilla: &c" + plugin.nightmare().getLevel() + " &8(&4" + plugin.nightmare().levelName() + "&8)");
    }

    private void evento(CommandSender s, String[] a) {
        if (a.length < 2) {
            say(s, "&7Eventos: &f" + String.join(", ", EVENTOS.keySet()) + ", detener");
            return;
        }
        if (a[1].equalsIgnoreCase("detener")) {
            plugin.events().stopAll();
            say(s, "&aEventos activos finalizados.");
            return;
        }
        String key = EVENTOS.get(a[1].toLowerCase(Locale.ROOT));
        Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
        if (key == null || target == null) {
            say(s, "&cUso: /pesadilla evento <nombre> [jugador]");
            return;
        }
        say(s, plugin.events().start(key, target, true) ? "&aEvento iniciado." : "&cNo se pudo iniciar (límite alcanzado).");
    }

    private void jefe(CommandSender s, String[] a) {
        if (a.length < 2) {
            say(s, "&7Jefes: &f" + String.join(", ", JEFES.keySet()) + "&7 | &fmundial&7, &fpunto <nombre> <radio>&7, &fpuntos&7, &fborrar <nombre>&7, &fcancelar");
            return;
        }
        String arg = a[1].toLowerCase(Locale.ROOT);
        BossManager bm = plugin.bosses();
        switch (arg) {
            case "puntos" -> {
                var pts = bm.points();
                say(s, "&7Puntos de aparición del jefe mundial: &f" + pts.size());
                for (BossManager.SpawnPoint p : pts) {
                    say(s, "&8- &5" + p.name() + " &7(" + p.world() + " " + (int) p.x() + ", " + (int) p.y() + ", " + (int) p.z() + " | radio " + (int) p.radius() + ")");
                }
            }
            case "punto" -> {
                if (!(s instanceof Player p) || a.length < 4) {
                    say(s, "&7Uso (como jugador, parado donde quieras el punto): &f/pesadilla jefe punto <nombre> <radio>");
                    return;
                }
                Integer r = CommandUtil.parseInt(a[3]);
                if (r == null || r < 1) {
                    say(s, "&cRadio inválido.");
                    return;
                }
                Location l = p.getLocation();
                bm.addPoint(new BossManager.SpawnPoint(a[2], l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), r));
                say(s, "&aPunto &f" + a[2] + " &acreado en &f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " &a(radio " + r + ").");
            }
            case "borrar" -> say(s, a.length > 2 && bm.removePoint(a[2]) ? "&aPunto eliminado." : "&cPunto no encontrado.");
            case "cancelar" -> {
                bm.cancelCountdown();
                say(s, "&aCuenta atrás cancelada.");
            }
            case "mundial" -> {
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pl ? pl : null);
                if (p == null) {
                    say(s, "&cIndica un jugador conectado.");
                    return;
                }
                say(s, bm.startWorldBoss(p, null) ? "&aCuenta atrás del jefe mundial iniciada." : "&cYa hay una cuenta atrás o se alcanzó el límite de jefes.");
            }
            default -> {
                String key = JEFES.get(arg);
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pl ? pl : null);
                if (key == null || p == null) {
                    say(s, "&cUso: /pesadilla jefe <nombre> [jugador]");
                    return;
                }
                if (!bm.canSpawnMore()) {
                    say(s, "&cLímite de jefes simultáneos alcanzado.");
                    return;
                }
                Location loc = plugin.spawns().spotNear(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(8)), 3);
                say(s, bm.spawn(key, loc, false) != null ? "&aJefe invocado." : "&cNo se pudo invocar el jefe.");
            }
        }
    }

    private void zona(CommandSender s, String[] a) {
        CursedZoneManager z = plugin.zones();
        String sub = a.length < 2 ? "lista" : a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "crear" -> {
                if (!(s instanceof Player p) || a.length < 4) {
                    say(s, "&7Uso (como jugador): &f/pesadilla zona crear <nombre> <radio>");
                    return;
                }
                Integer r = CommandUtil.parseInt(a[3]);
                if (r == null || r < 3) {
                    say(s, "&cRadio inválido (mínimo 3).");
                    return;
                }
                Location l = p.getLocation();
                z.add(new CursedZoneManager.Zone(a[2], l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), r));
                say(s, "&5Zona maldita &f" + a[2] + " &5creada (radio " + r + ").");
            }
            case "borrar" -> say(s, a.length > 2 && z.remove(a[2]) ? "&aZona eliminada." : "&cZona no encontrada.");
            default -> {
                say(s, "&7Zonas malditas: &f" + z.zones().size());
                for (CursedZoneManager.Zone zone : z.zones()) {
                    say(s, "&8- &5" + zone.name() + " &7(" + zone.world() + " " + (int) zone.x() + ", " + (int) zone.y() + ", " + (int) zone.z() + " | radio " + (int) zone.radius() + ")");
                }
            }
        }
    }

    /** Invoca un mob especial delante de ti para PROBARLO: pollo explosivo, ghast demoníaco, piglin. */
    private void invocar(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 2) {
            say(s, "&7Uso (como jugador): &f/pesadilla invocar <pollo|ghast|piglin>");
            return;
        }
        Location loc = plugin.spawns().spotNear(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(6)), 2);
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "pollo" -> {
                var e = plugin.spawns().spawnAt(EntityType.CHICKEN, loc, p.getUniqueId(), null);
                if (e instanceof Chicken c) plugin.nmMobs().makeExplosiveChicken(c);
                say(s, "&aPollo explosivo invocado. ¡Corre!");
            }
            case "ghast" -> {
                var e = plugin.spawns().spawnAt(EntityType.GHAST, loc.clone().add(0, 8, 0), p.getUniqueId(), null);
                say(s, e != null ? "&aGhast invocado (es demoníaco con módulo nm_ghast activo)." : "&cNo se pudo invocar.");
            }
            case "piglin" -> {
                var e = plugin.spawns().spawnAt(EntityType.PIGLIN, loc, p.getUniqueId(), null);
                say(s, e != null ? "&aPiglin invocado (siempre enojado con módulo nm_piglins activo)." : "&cNo se pudo invocar.");
            }
            default -> say(s, "&cUso: /pesadilla invocar <pollo|ghast|piglin>");
        }
    }

    private void ayuda(CommandSender s) {
        say(s, "&8&m        &r &4☠ Pesadilla &8&m        ");
        for (String l : List.of(
                "&c/pesadilla nivel &7[fijar <n|auto>]",
                "&c/pesadilla evento &7<" + String.join("|", EVENTOS.keySet()) + "|detener> [jugador]",
                "&c/pesadilla jefe &7<" + String.join("|", JEFES.keySet()) + "|mundial> [jugador]",
                "&c/pesadilla jefe &7punto <nombre> <radio> | puntos | borrar <nombre> | cancelar",
                "&c/pesadilla zona &7crear <nombre> <radio> | borrar <nombre> | lista",
                "&c/pesadilla invocar &7<pollo|ghast|piglin> &8- &7para probar",
                "&c/pesadilla estado &7| &climpiar",
                "&8Todo lo demás se configura en &f/panel")) say(s, l);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!CommandUtil.has(sender, "extreme.nightmare")) return out;
        if (a.length == 1) return CommandUtil.filter(List.of("nivel", "evento", "jefe", "zona", "invocar", "estado", "limpiar"), a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            switch (sub) {
                case "nivel" -> out.add("fijar");
                case "evento" -> {
                    out.addAll(EVENTOS.keySet());
                    out.add("detener");
                }
                case "jefe" -> {
                    out.addAll(JEFES.keySet());
                    out.addAll(List.of("mundial", "punto", "puntos", "borrar", "cancelar"));
                }
                case "zona" -> out.addAll(List.of("crear", "borrar", "lista"));
                case "invocar" -> out.addAll(List.of("pollo", "ghast", "piglin"));
                default -> { }
            }
        } else if (a.length == 3) {
            if (sub.equals("nivel")) out.addAll(List.of("auto", "1", "5", "10", "20"));
            else if (sub.equals("evento") || (sub.equals("jefe") && !List.of("punto", "borrar").contains(a[1].toLowerCase(Locale.ROOT)))) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (sub.equals("jefe") && a[1].equalsIgnoreCase("borrar")) {
                plugin.bosses().points().forEach(p -> out.add(p.name()));
            } else if (sub.equals("zona") && a[1].equalsIgnoreCase("borrar")) {
                plugin.zones().zones().forEach(z -> out.add(z.name()));
            }
        }
        return CommandUtil.filter(out, a[a.length - 1]);
    }
}
