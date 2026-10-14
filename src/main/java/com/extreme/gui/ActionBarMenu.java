package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ActionBarMenu extends Menu {

    public ActionBarMenu(ExtremePlugin plugin) {
        super(plugin, 4, plugin.messages().str("gui.actionbar.title"));
    }

    @Override
    protected void render() {
        border(Material.ORANGE_STAINED_GLASS_PANE);
        YamlConfiguration c = plugin.configs().get("actionbar.yml");
        boolean on = c.getBoolean("actionbar.enabled", true);
        boolean elements = "ELEMENTS".equalsIgnoreCase(c.getString("actionbar.mode", "ELEMENTS"));

        set(10, toggleItem(on, g("actionbar.toggle-name", "state", state(on)), l("actionbar.toggle-lore")), e -> {
            plugin.configs().set("actionbar.yml", "actionbar.enabled", !on);
            plugin.actionBar().reload();
            refresh();
        });
        set(12, ItemBuilder.of(Material.WRITABLE_BOOK, g("actionbar.mode-name", "mode", elements ? "ELEMENTS" : "FORMAT"),
                l("actionbar.mode-lore")), e -> {
            plugin.configs().set("actionbar.yml", "actionbar.mode", elements ? "FORMAT" : "ELEMENTS");
            plugin.actionBar().reload();
            refresh();
        });

        ConfigurationSection sec = c.getConfigurationSection("actionbar.elements");
        List<String> names = new ArrayList<>();
        if (sec != null) names.addAll(sec.getKeys(false));
        for (int i = 0; i < names.size() && i < 7; i++) {
            String name = names.get(i);
            boolean enabled = c.getBoolean("actionbar.elements." + name + ".enabled", true);
            set(19 + i, toggleItem(enabled, g("actionbar.element-name", "name", name, "state", state(enabled)),
                    l("actionbar.element-lore", "mode", elements ? "" : g("actionbar.element-inactive"))), e -> {
                plugin.configs().set("actionbar.yml", "actionbar.elements." + name + ".enabled", !enabled);
                plugin.actionBar().reload();
                refresh();
            });
        }

        backButton(31, () -> new MainMenu(plugin));
    }
}
