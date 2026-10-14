package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.lives.PlayerData;
import com.extreme.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Lista paginada de jugadores. Izq: +1 vida · Der: -1 vida · Shift+Izq: reiniciar. */
public class PlayersMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public PlayersMenu(ExtremePlugin plugin, int page) {
        super(plugin, 6, plugin.messages().str("gui.players.title"));
        this.page = page;
    }

    @Override
    protected void render() {
        List<PlayerData> all = plugin.lives().all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int max = plugin.lives().getMaxLives();

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            PlayerData d = all.get(start + i);
            set(i, ItemBuilder.head(Bukkit.getOfflinePlayer(d.id), g("players.entry-name", "player", d.name),
                    l("players.entry-lore", "lives", d.lives, "max_lives", max,
                            "hearts", "❤".repeat(Math.max(0, Math.min(d.lives, 30))))), e -> {
                ClickType t = e.getClick();
                if (t == ClickType.LEFT) plugin.lives().addLives(d, 1);
                else if (t == ClickType.RIGHT) plugin.lives().addLives(d, -1);
                else if (t == ClickType.SHIFT_LEFT) plugin.lives().resetLives(d);
                plugin.lives().saveIfDirty();
                refresh();
            });
        }

        if (page > 0) {
            set(45, ItemBuilder.of(Material.ARROW, g("players.prev")), e -> {
                page--;
                refresh();
            });
        }
        set(49, ItemBuilder.of(Material.BARRIER, g("back")), e -> new MainMenu(plugin).open(who(e)));
        set(50, ItemBuilder.of(Material.PAPER, g("players.page", "page", page + 1, "pages", pages)));
        if (page < pages - 1) {
            set(53, ItemBuilder.of(Material.ARROW, g("players.next")), e -> {
                page++;
                refresh();
            });
        }
    }
}
