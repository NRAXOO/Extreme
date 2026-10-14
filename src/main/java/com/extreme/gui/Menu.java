package com.extreme.gui;

import com.extreme.ExtremePlugin;
import com.extreme.utils.ItemBuilder;
import com.extreme.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Base de todos los menús: un inventario, acciones por slot y re-dibujado sin reabrir. */
public abstract class Menu implements InventoryHolder {

    protected final ExtremePlugin plugin;
    private final Inventory inventory;
    private final int rows;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(ExtremePlugin plugin, int rows, String title) {
        this.plugin = plugin;
        this.rows = rows;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.color(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Dibuja el contenido. Se llama cada vez que hay que refrescar. */
    protected abstract void render();

    public final void open(Player p) {
        refresh();
        p.openInventory(inventory);
    }

    public final void refresh() {
        actions.clear();
        inventory.clear();
        render();
    }

    final void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("extreme.admin")) {
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() != inventory) return;
        Consumer<InventoryClickEvent> action = actions.get(e.getSlot());
        if (action == null) return;
        plugin.sounds().playTo(p, "gui-click");
        action.accept(e);
    }

    // ------------------------------------------------------------------
    //  Utilidades para las subclases
    // ------------------------------------------------------------------

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void border(Material m) {
        ItemStack filler = ItemBuilder.filler(m);
        for (int i = 0; i < rows * 9; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) inventory.setItem(i, filler);
        }
    }

    protected void backButton(int slot, Supplier<Menu> target) {
        set(slot, ItemBuilder.of(Material.ARROW, g("back")), e -> target.get().open(who(e)));
    }

    protected static Player who(InventoryClickEvent e) {
        return (Player) e.getWhoClicked();
    }

    protected String g(String key, Object... pairs) {
        return plugin.messages().str("gui." + key, pairs);
    }

    protected List<String> l(String key, Object... pairs) {
        return plugin.messages().list("gui." + key, pairs);
    }

    protected String state(boolean on) {
        return plugin.messages().state(on);
    }

    protected ItemStack toggleItem(boolean on, String name, List<String> lore) {
        return ItemBuilder.of(on ? Material.LIME_DYE : Material.GRAY_DYE, name, lore);
    }
}
