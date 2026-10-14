package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.modules.ModuleManager;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Material;

/** Panel principal organizado por secciones. Cada sección se abre en su propio panel. */
public class MainMenu extends Menu {

    public MainMenu(ExtremePlugin plugin) {
        super(plugin, 5, plugin.messages().str("gui.main.title"));
    }

    @Override
    protected void render() {
        border(Material.BLACK_STAINED_GLASS_PANE);
        String hint = g("hint-open");
        ModuleManager mods = plugin.modules();

        // ---- Fila 1: sistema base ----
        set(10, ItemBuilder.of(Material.DIAMOND_SWORD, g("main.pvp-name"),
                l("main.pvp-lore", "state", state(plugin.pvp().isEnabled()), "random", state(plugin.pvp().isRandomEnabled()), "hint", hint)),
                e -> new PVPMenu(plugin).open(who(e)));
        set(12, ItemBuilder.of(Material.TOTEM_OF_UNDYING, g("main.lives-name"),
                l("main.lives-lore", "state", state(plugin.lives().isEnabled()), "default", plugin.lives().getDefaultLives(),
                        "max", plugin.lives().getMaxLives(), "hint", hint)),
                e -> new LivesMenu(plugin).open(who(e)));
        set(14, ItemBuilder.of(Material.NAME_TAG, g("main.actionbar-name"),
                l("main.actionbar-lore", "state", state(plugin.modules().on("actionbar")), "hint", hint)),
                e -> new ActionBarMenu(plugin).open(who(e)));
        set(16, ItemBuilder.of(Material.CLOCK, g("main.day-name"),
                l("main.day-lore", "day", plugin.days().getDay(), "max_day", plugin.days().getMaxDays(), "hint", hint)),
                e -> new DayMenu(plugin).open(who(e)));

        // ---- Fila 2: dificultad, mobs, pesadilla, venganza ----
        set(19, ItemBuilder.of(Material.WITHER_SKELETON_SKULL, g("main.difficulty-name"),
                l("main.difficulty-lore", "state", state(plugin.difficulty().isEnabled()), "hint", hint)),
                e -> new ModuleSettingsMenu(plugin, mods.get("difficulty"), () -> new MainMenu(plugin)).open(who(e)));
        set(21, ItemBuilder.of(Material.ZOMBIE_HEAD, g("main.mobs-name"),
                l("main.mobs-lore", "on", mods.countOn(ModuleManager.Category.MOBS), "total", mods.countTotal(ModuleManager.Category.MOBS), "hint", hint)),
                e -> new ModulesMenu(plugin, ModuleManager.Category.MOBS, 0).open(who(e)));
        set(23, ItemBuilder.of(Material.DRAGON_HEAD, g("main.nightmare-name"),
                l("main.nightmare-lore", "state", state(mods.on("nightmare")), "level", plugin.nightmare().getLevel(),
                        "name", plugin.nightmare().levelName(), "on", mods.countOn(ModuleManager.Category.NIGHTMARE),
                        "total", mods.countTotal(ModuleManager.Category.NIGHTMARE), "hint", hint)),
                e -> new ModulesMenu(plugin, ModuleManager.Category.NIGHTMARE, 0).open(who(e)));
        set(25, ItemBuilder.of(Material.IRON_SWORD, g("main.revenge-name"),
                l("main.revenge-lore", "state", state(mods.on("revenge")), "hint", hint)),
                e -> new ModuleSettingsMenu(plugin, mods.get("revenge"), () -> new MainMenu(plugin)).open(who(e)));

        // ---- Fila 3: herramientas ----
        set(29, ItemBuilder.of(Material.PLAYER_HEAD, g("main.players-name"),
                l("main.players-lore", "count", plugin.lives().all().size(), "hint", hint)),
                e -> new PlayersMenu(plugin, 0).open(who(e)));
        set(31, ItemBuilder.of(Material.COMPARATOR, g("main.reload-name"), l("main.reload-lore")), e -> {
            plugin.reportReload(who(e), plugin.reloadAll());
            refresh();
        });
        set(33, ItemBuilder.of(Material.BOOK, g("main.base-modules-name"), l("main.base-modules-lore", "hint", hint)),
                e -> new ModulesMenu(plugin, ModuleManager.Category.BASE, 0).open(who(e)));

        set(40, ItemBuilder.of(Material.BARRIER, g("close")), e -> who(e).closeInventory());
    }
}
