package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.modules.ModuleManager;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de módulos de una categoría. Clic izquierdo = activar/desactivar, clic derecho = configurar.
 * Cada sistema se activa por separado para que el servidor no se sature de golpe.
 */
public class ModulesMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final ModuleManager.Category category;
    private int page;

    public ModulesMenu(ExtremePlugin plugin, ModuleManager.Category category, int page) {
        super(plugin, 6, plugin.messages().str("gui.modules.title-" + category.name().toLowerCase()));
        this.category = category;
        this.page = page;
    }

    @Override
    protected void render() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        List<ModuleManager.Feature> list = plugin.modules().byCategory(category);
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < SLOTS.length && page * SLOTS.length + i < list.size(); i++) {
            ModuleManager.Feature f = list.get(page * SLOTS.length + i);
            boolean on = plugin.modules().on(f.key);
            String name = plugin.messages().str("modules." + f.key + ".name");
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().str("modules." + f.key + ".desc"));
            lore.add("");
            if (f.locked) {
                lore.add(g("modules.always-on"));
            } else {
                lore.add(g("modules.state-line", "state", state(on)));
                lore.add(g("modules.hint-toggle"));
            }
            if (f.detail != null || !f.settings.isEmpty()) lore.add(g("modules.hint-config"));
            var item = ItemBuilder.of(f.icon, (f.locked ? "&e" : on ? "&a" : "&7") + name, lore);
            ItemBuilder.glow(item, on && !f.locked);
            set(SLOTS[i], item, e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    openDetail(who(e), f);
                    return;
                }
                if (f.locked) {
                    openDetail(who(e), f);
                    return;
                }
                plugin.modules().set(f.key, !plugin.modules().on(f.key));
                refresh();
            });
        }

        // Activar / desactivar todos (excepto el interruptor maestro)
        if (category == ModuleManager.Category.NIGHTMARE || category == ModuleManager.Category.MOBS) {
            set(47, ItemBuilder.of(Material.RED_CONCRETE, g("modules.disable-all"), l("modules.disable-all-lore")), e -> bulk(false));
            set(51, ItemBuilder.of(Material.LIME_CONCRETE, g("modules.enable-all"), l("modules.enable-all-lore")), e -> bulk(true));
        }
        if (page > 0) set(45, ItemBuilder.of(Material.ARROW, g("players.prev")), e -> {
            page--;
            refresh();
        });
        if (page < pages - 1) set(53, ItemBuilder.of(Material.ARROW, g("players.next")), e -> {
            page++;
            refresh();
        });
        set(49, ItemBuilder.of(Material.ARROW, g("back")), e -> new MainMenu(plugin).open(who(e)));
        set(4, ItemBuilder.of(Material.BOOK, g("modules.summary", "on", plugin.modules().countOn(category),
                "total", plugin.modules().countTotal(category)), l("modules.summary-lore")));
    }

    private void bulk(boolean value) {
        for (ModuleManager.Feature f : plugin.modules().byCategory(category)) {
            if (f.locked || f.key.equals("nightmare")) continue;
            if (plugin.modules().on(f.key) != value) plugin.modules().set(f.key, value);
        }
        refresh();
    }

    private void openDetail(org.bukkit.entity.Player p, ModuleManager.Feature f) {
        if (f.detail != null) {
            f.detail.apply(plugin).open(p);
        } else if (!f.settings.isEmpty()) {
            new ModuleSettingsMenu(plugin, f, () -> new ModulesMenu(plugin, category, page)).open(p);
        } else {
            plugin.messages().send(p, "module-no-settings");
        }
    }
}
