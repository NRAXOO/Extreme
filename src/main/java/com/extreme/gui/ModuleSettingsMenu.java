package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.modules.ModuleManager;
import com.extreme.utils.ItemBuilder;
import com.extreme.utils.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Panel de ajustes de un módulo: clic izquierdo +, derecho -, Shift = cambio grande. Se guarda al instante. */
public class ModuleSettingsMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final ModuleManager.Feature feature;
    private final Supplier<Menu> back;

    public ModuleSettingsMenu(ExtremePlugin plugin, ModuleManager.Feature feature, Supplier<Menu> back) {
        super(plugin, 5, plugin.messages().str("gui.settings.title", "name", plugin.messages().str("modules." + feature.key + ".name")));
        this.feature = feature;
        this.back = back;
    }

    @Override
    protected void render() {
        border(Material.BLUE_STAINED_GLASS_PANE);
        boolean on = plugin.modules().on(feature.key);

        if (!feature.locked) {
            set(4, toggleItem(on, g("settings.toggle", "name", plugin.messages().str("modules." + feature.key + ".name"), "state", state(on)),
                    l("settings.toggle-lore")), e -> {
                plugin.modules().set(feature.key, !plugin.modules().on(feature.key));
                refresh();
            });
        }

        List<ModuleManager.Setting> settings = feature.settings;
        for (int i = 0; i < SLOTS.length && i < settings.size(); i++) {
            ModuleManager.Setting s = settings.get(i);
            YamlConfiguration c = plugin.configs().get(s.file());
            String shown = display(s, c);
            boolean bool = s.type() == ModuleManager.Type.BOOL;
            boolean boolOn = bool && c.getBoolean(s.path(), false);
            Material icon = bool ? (boolOn ? Material.LIME_DYE : Material.GRAY_DYE) : Material.COMPARATOR;
            set(SLOTS[i], ItemBuilder.of(icon, g("settings.entry-name", "label", s.label(), "value", shown),
                    l(bool ? "settings.entry-lore-bool" : "settings.entry-lore", "path", s.path())), e -> change(e, s));
        }
        set(40, ItemBuilder.of(Material.ARROW, g("back")), e -> back.get().open(who(e)));
        set(36, ItemBuilder.of(Material.COMPARATOR, g("settings.reload"), l("settings.reload-lore")), e -> {
            plugin.reportReload(who(e), plugin.reloadAll());
            refresh();
        });
    }

    private String display(ModuleManager.Setting s, YamlConfiguration c) {
        return switch (s.type()) {
            case BOOL -> state(c.getBoolean(s.path(), false));
            case INT -> String.valueOf(c.getInt(s.path()));
            case TIME -> TimeUtil.format(TimeUtil.parse(c.getString(s.path()), 0));
            default -> trim(c.getDouble(s.path()));
        };
    }

    private String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String t = String.format(Locale.ROOT, "%.4f", v);
        return t.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void change(InventoryClickEvent e, ModuleManager.Setting s) {
        YamlConfiguration c = plugin.configs().get(s.file());
        double dir = e.isRightClick() ? -1 : 1;
        double mult = e.isShiftClick() ? 5 : 1;
        switch (s.type()) {
            case BOOL -> plugin.configs().set(s.file(), s.path(), !c.getBoolean(s.path(), false));
            case INT -> {
                int v = (int) Math.round(Math.max(s.min(), Math.min(s.max(), c.getInt(s.path()) + dir * mult * s.step())));
                plugin.configs().set(s.file(), s.path(), v);
            }
            case TIME -> {
                long cur = TimeUtil.parse(c.getString(s.path()), (long) s.min());
                long v = (long) Math.max(s.min(), Math.min(s.max(), cur + dir * mult * s.step()));
                plugin.configs().set(s.file(), s.path(), TimeUtil.format(v));
            }
            default -> {
                double v = Math.max(s.min(), Math.min(s.max(), c.getDouble(s.path()) + dir * mult * s.step()));
                plugin.configs().set(s.file(), s.path(), Math.round(v * 10000.0) / 10000.0);
            }
        }
        plugin.applyConfig();
        refresh();
    }
}
