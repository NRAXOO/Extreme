package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.utils.ItemBuilder;
import com.extreme.utils.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;

public class PVPMenu extends Menu {

    public PVPMenu(ExtremePlugin plugin) {
        super(plugin, 4, plugin.messages().str("gui.pvp.title"));
    }

    @Override
    protected void render() {
        border(Material.RED_STAINED_GLASS_PANE);
        boolean on = plugin.pvp().isEnabled();
        boolean random = plugin.pvp().isRandomEnabled();
        YamlConfiguration c = plugin.configs().get("pvp.yml");
        long min = TimeUtil.parse(c.getString("pvp.random.min-time"), 1_800_000L);
        long max = TimeUtil.parse(c.getString("pvp.random.max-time"), 7_200_000L);

        set(10, ItemBuilder.of(Material.LIME_CONCRETE, g("pvp.enable-name"), l("pvp.enable-lore")), e -> {
            if (!plugin.pvp().set(true, true)) plugin.messages().send(who(e), "pvp-already-on");
            refresh();
        });
        set(12, ItemBuilder.of(Material.RED_CONCRETE, g("pvp.disable-name"), l("pvp.disable-lore")), e -> {
            if (!plugin.pvp().set(false, true)) plugin.messages().send(who(e), "pvp-already-off");
            refresh();
        });
        set(14, toggleItem(random, g("pvp.random-name", "state", state(random)), l("pvp.random-lore")), e -> {
            plugin.pvp().setRandom(!plugin.pvp().isRandomEnabled());
            refresh();
        });
        set(16, ItemBuilder.of(Material.COMPASS, g("pvp.info-name"),
                l("pvp.info-lore", "state", state(on), "random", state(random), "timer", plugin.pvp().timerText())));

        set(20, ItemBuilder.of(Material.HOPPER, g("pvp.min-name", "time", TimeUtil.format(min)), l("pvp.time-lore")),
                e -> {
                    adjust(true, step(e));
                    refresh();
                });
        set(24, ItemBuilder.of(Material.HOPPER, g("pvp.max-name", "time", TimeUtil.format(max)), l("pvp.time-lore")),
                e -> {
                    adjust(false, step(e));
                    refresh();
                });

        backButton(31, () -> new MainMenu(plugin));
    }

    /** Clic izq +5m / der -5m. Con shift: 30m. */
    private long step(InventoryClickEvent e) {
        long base = e.isShiftClick() ? 1_800_000L : 300_000L;
        return e.isRightClick() ? -base : base;
    }

    private void adjust(boolean isMin, long delta) {
        YamlConfiguration c = plugin.configs().get("pvp.yml");
        long min = TimeUtil.parse(c.getString("pvp.random.min-time"), 1_800_000L);
        long max = TimeUtil.parse(c.getString("pvp.random.max-time"), 7_200_000L);
        if (isMin) {
            min = Math.max(10_000L, min + delta);
            if (min > max) max = min;
        } else {
            max = Math.max(10_000L, max + delta);
            if (max < min) min = max;
        }
        plugin.configs().set("pvp.yml", "pvp.random.min-time", TimeUtil.format(min));
        plugin.configs().set("pvp.yml", "pvp.random.max-time", TimeUtil.format(max));
    }
}
