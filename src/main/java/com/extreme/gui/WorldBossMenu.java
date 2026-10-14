package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.modules.ModuleManager;
import com.extreme.nightmare.BossManager;
import com.extreme.utils.ItemBuilder;
import com.extreme.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Jefe mundial: elegir DÓNDE aparece (puntos del mundo), ver la cuenta atrás e invocarlo. */
public class WorldBossMenu extends Menu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public WorldBossMenu(ExtremePlugin plugin) {
        super(plugin, 5, plugin.messages().str("gui.worldboss.title"));
    }

    @Override
    protected void render() {
        border(Material.PURPLE_STAINED_GLASS_PANE);
        BossManager bm = plugin.bosses();
        int radius = plugin.configs().get("nightmare.yml").getInt("world-boss.default-radius", 40);

        set(10, ItemBuilder.of(Material.BEACON, g("worldboss.add-name"), l("worldboss.add-lore", "radius", radius)), e -> {
            Player p = who(e);
            Location l = p.getLocation();
            String name = "punto-" + (bm.points().size() + 1);
            bm.addPoint(new BossManager.SpawnPoint(name, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), radius));
            plugin.messages().send(p, "worldboss-point-added", "name", name, "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
            refresh();
        });
        set(11, ItemBuilder.of(Material.COMPASS, g("worldboss.radius-name", "radius", radius), l("worldboss.radius-lore")), e -> {
            int v = Math.max(5, Math.min(500, radius + (e.isRightClick() ? -10 : 10) * (e.isShiftClick() ? 5 : 1)));
            plugin.configs().set("nightmare.yml", "world-boss.default-radius", v);
            refresh();
        });

        String status;
        if (bm.isCountdownActive()) status = g("worldboss.status-active", "time", TimeUtil.clock(bm.countdownRemainingMs()), "coords", bm.countdownCoords());
        else {
            long next = bm.nextScheduledMs();
            status = next < 0 ? g("worldboss.status-off") : g("worldboss.status-next", "time", TimeUtil.format(next));
        }
        set(13, ItemBuilder.of(Material.CLOCK, g("worldboss.status-name"), List.of(status)));

        set(15, ItemBuilder.of(Material.NETHER_STAR, g("worldboss.summon-name"), l("worldboss.summon-lore")), e -> {
            boolean ok = bm.startWorldBoss(who(e), null);
            plugin.messages().send(who(e), ok ? "worldboss-started" : "worldboss-busy");
            refresh();
        });
        set(16, ItemBuilder.of(Material.BARRIER, g("worldboss.cancel-name"), l("worldboss.cancel-lore")), e -> {
            bm.cancelCountdown();
            refresh();
        });

        List<BossManager.SpawnPoint> pts = bm.points();
        for (int i = 0; i < SLOTS.length && i < pts.size(); i++) {
            BossManager.SpawnPoint sp = pts.get(i);
            set(SLOTS[i], ItemBuilder.of(Material.RESPAWN_ANCHOR, g("worldboss.point-name", "name", sp.name()),
                    l("worldboss.point-lore", "world", sp.world(), "x", (int) sp.x(), "y", (int) sp.y(), "z", (int) sp.z(), "radius", (int) sp.radius())), e -> {
                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    bm.removePoint(sp.name());
                    refresh();
                } else {
                    World w = Bukkit.getWorld(sp.world());
                    if (w != null) who(e).teleport(new Location(w, sp.x(), sp.y(), sp.z()));
                }
            });
        }

        ModuleManager.Feature f = plugin.modules().get("nm_world_boss");
        set(39, ItemBuilder.of(Material.COMPARATOR, g("worldboss.settings-name"), l("worldboss.settings-lore")),
                e -> new ModuleSettingsMenu(plugin, f, () -> new WorldBossMenu(plugin)).open(who(e)));
        set(40, ItemBuilder.of(Material.ARROW, g("back")), e -> new ModulesMenu(plugin, ModuleManager.Category.NIGHTMARE, 0).open(who(e)));
    }
}
