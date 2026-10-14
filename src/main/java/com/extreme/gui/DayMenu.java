package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Material;

public class DayMenu extends Menu {

    public DayMenu(ExtremePlugin plugin) {
        super(plugin, 4, plugin.messages().str("gui.day.title"));
    }

    @Override
    protected void render() {
        border(Material.YELLOW_STAINED_GLASS_PANE);
        int day = plugin.days().getDay();
        int max = plugin.days().getMaxDays();

        change(10, -5, Material.RED_CONCRETE);
        change(11, -1, Material.ORANGE_CONCRETE);
        set(13, ItemBuilder.of(Material.CLOCK, g("day.current-name", "day", day, "max_day", max),
                l("day.current-lore", "difficulty", plugin.difficulty().getLabel())));
        change(15, 1, Material.LIME_CONCRETE);
        change(16, 5, Material.GREEN_CONCRETE);

        set(20, ItemBuilder.of(Material.WHITE_CONCRETE, g("day.first-name"), l("day.set-lore")), e -> {
            plugin.days().setDay(1, true);
            refresh();
        });
        set(24, ItemBuilder.of(Material.BLACK_CONCRETE, g("day.last-name", "max_day", max), l("day.set-lore")), e -> {
            plugin.days().setDay(plugin.days().getMaxDays(), true);
            refresh();
        });

        backButton(31, () -> new MainMenu(plugin));
    }

    private void change(int slot, int delta, Material icon) {
        String name = g(delta > 0 ? "day.plus-name" : "day.minus-name", "amount", Math.abs(delta));
        set(slot, ItemBuilder.of(icon, name, l("day.set-lore")), e -> {
            plugin.days().setDay(plugin.days().getDay() + delta, true);
            refresh();
        });
    }
}
