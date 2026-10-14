package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

public class LivesMenu extends Menu {

    public LivesMenu(ExtremePlugin plugin) {
        super(plugin, 4, plugin.messages().str("gui.lives.title"));
    }

    @Override
    protected void render() {
        border(Material.PINK_STAINED_GLASS_PANE);
        boolean on = plugin.lives().isEnabled();
        boolean ban = plugin.lives().isBanOnZero();

        set(10, toggleItem(on, g("lives.toggle-name", "state", state(on)), l("lives.toggle-lore")), e -> {
            plugin.lives().setEnabled(!plugin.lives().isEnabled());
            refresh();
        });
        set(12, ItemBuilder.of(Material.TOTEM_OF_UNDYING, g("lives.default-name", "value", plugin.lives().getDefaultLives()),
                l("lives.adjust-lore")), e -> {
            int v = plugin.lives().getDefaultLives() + delta(e);
            int max = plugin.lives().getMaxLives();
            plugin.configs().set("lives.yml", "lives.default", Math.max(1, Math.min(v, max)));
            plugin.lives().reload();
            refresh();
        });
        set(14, ItemBuilder.of(Material.GOLDEN_APPLE, g("lives.max-name", "value", plugin.lives().getMaxLives()),
                l("lives.adjust-lore")), e -> {
            int v = Math.max(1, plugin.lives().getMaxLives() + delta(e));
            plugin.configs().set("lives.yml", "lives.maximum", v);
            if (plugin.lives().getDefaultLives() > v) plugin.configs().set("lives.yml", "lives.default", v);
            plugin.lives().reload();
            refresh();
        });
        set(16, toggleItem(ban, g("lives.ban-name", "state", state(ban)), l("lives.ban-lore")), e -> {
            plugin.configs().set("lives.yml", "lives.ban-on-zero", !plugin.lives().isBanOnZero());
            plugin.lives().reload();
            refresh();
        });
        set(22, ItemBuilder.of(Material.PLAYER_HEAD, g("lives.players-name"), l("lives.players-lore")),
                e -> new PlayersMenu(plugin, 0).open(who(e)));

        backButton(31, () -> new MainMenu(plugin));
    }

    /** Clic izq +1 / der -1. Con shift: 5. */
    private int delta(InventoryClickEvent e) {
        int base = e.isShiftClick() ? 5 : 1;
        return e.isRightClick() ? -base : base;
    }
}
