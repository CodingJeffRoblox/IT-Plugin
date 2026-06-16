package com.itplugin.gui;

import com.itplugin.ITPlugin;
import com.itplugin.ticket.Ticket;
import com.itplugin.ticket.TicketStatus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff Dashboard — 54-slot overview panel showing live IT stats.
 *
 *  Layout:
 *   Slot 10: Open Tickets summary
 *   Slot 12: Closed/Resolved Tickets
 *   Slot 14: Console Errors captured
 *   Slot 16: IT Notes count
 *   Slot 28: Online players
 *   Slot 30: Server Monitor status
 *   Slot 32: Config validator (quick run)
 *   Slot 34: Knowledge Base shortcut
 *   Slot 49: Close
 */
public class StaffDashboardGUI implements InventoryHolder {

    public static final int SIZE = 54;

    public static final int SLOT_OPEN_TICKETS  = 10;
    public static final int SLOT_CLOSED        = 12;
    public static final int SLOT_ERRORS        = 14;
    public static final int SLOT_NOTES         = 16;
    public static final int SLOT_PLAYERS       = 28;
    public static final int SLOT_MONITOR       = 30;
    public static final int SLOT_VALIDATE      = 32;
    public static final int SLOT_KB            = 34;
    public static final int SLOT_CLOSE         = 49;

    private final ITPlugin plugin;
    private final Inventory inventory;

    public StaffDashboardGUI(ITPlugin plugin, Player viewer) {
        this.plugin = plugin;
        var mm = plugin.getMessageManager();
        this.inventory = Bukkit.createInventory(this, SIZE, mm.color("&8IT Staff Dashboard"));
        build(viewer);
    }

    private void build(Player viewer) {
        var mm = plugin.getMessageManager();

        // Border fill
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, border);
        // Clear inner slots
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                inventory.setItem(row * 9 + col, null);

        // Open tickets
        List<Ticket> open   = plugin.getTicketManager().getOpenTickets();
        List<Ticket> closed = new ArrayList<>(plugin.getTicketManager().getAllTickets());
        closed.removeIf(t -> t.getStatus() != TicketStatus.CLOSED);
        int openCount   = open.size();
        int closedCount = closed.size();

        inventory.setItem(SLOT_OPEN_TICKETS,
            new ItemBuilder(Material.BOOK)
                .name(mm.color("&e&lOpen Tickets"))
                .lore(mm.color("&7Currently: &e" + openCount),
                      mm.color("&8Click to view all open tickets"))
                .glowing(openCount > 0)
                .build());

        inventory.setItem(SLOT_CLOSED,
            new ItemBuilder(Material.ENDER_CHEST)
                .name(mm.color("&a&lClosed Tickets"))
                .lore(mm.color("&7Resolved: &a" + closedCount))
                .build());

        // Console errors
        int errCount = plugin.getConsoleMonitor().getCapturedErrors().size();
        inventory.setItem(SLOT_ERRORS,
            new ItemBuilder(Material.REDSTONE_TORCH)
                .name(mm.color("&c&lConsole Errors"))
                .lore(mm.color("&7Captured: &c" + errCount),
                      mm.color("&8Click to view error log"))
                .glowing(errCount > 0)
                .build());

        // IT Notes
        int notesCount = plugin.getNoteManager().getAllNotes().size();
        inventory.setItem(SLOT_NOTES,
            new ItemBuilder(Material.WRITABLE_BOOK)
                .name(mm.color("&b&lIT Notes"))
                .lore(mm.color("&7Saved articles: &b" + notesCount),
                      mm.color("&8Click to browse knowledge base"))
                .build());

        // Online players
        int online = Bukkit.getOnlinePlayers().size();
        int max    = Bukkit.getMaxPlayers();
        inventory.setItem(SLOT_PLAYERS,
            new ItemBuilder(Material.PLAYER_HEAD)
                .name(mm.color("&f&lOnline Players"))
                .lore(mm.color("&7" + online + " / " + max + " slots"))
                .build());

        // Server monitor
        boolean monEnabled = plugin.getConfig().getBoolean("server-monitor.enabled", true);
        inventory.setItem(SLOT_MONITOR,
            new ItemBuilder(monEnabled ? Material.COMPASS : Material.GRAY_DYE)
                .name(mm.color("&d&lServer Monitor"))
                .lore(mm.color(monEnabled ? "&aEnabled" : "&cDisabled"),
                      mm.color("&8BungeeCord cross-server tracking"))
                .build());

        // Validate
        inventory.setItem(SLOT_VALIDATE,
            new ItemBuilder(Material.COMPARATOR)
                .name(mm.color("&6&lConfig Validator"))
                .lore(mm.color("&7Click to scan all plugin configs"))
                .build());

        // Knowledge base shortcut
        inventory.setItem(SLOT_KB,
            new ItemBuilder(Material.BOOKSHELF)
                .name(mm.color("&b&lKnowledge Base"))
                .lore(mm.color("&7IT articles and solutions"),
                      mm.color("&8" + notesCount + " articles saved"))
                .build());

        // Close
        inventory.setItem(SLOT_CLOSE,
            new ItemBuilder(Material.BARRIER)
                .name(mm.color("&cClose"))
                .build());
    }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }
}
