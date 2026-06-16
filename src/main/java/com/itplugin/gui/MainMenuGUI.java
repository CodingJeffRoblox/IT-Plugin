package com.itplugin.gui;

import com.itplugin.ITPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * IT CENTER main menu GUI — 54-slot, 5×2 icon grid.
 *
 * Layout (6 rows × 9 columns):
 *   Row 0: ─ black glass border ─
 *   Row 1: [TICKETS] | [STAFF DASHBOARD] | [SERVER MONITOR] | [CONSOLE TERMINAL] | [KNOWLEDGE BASE]
 *   Row 2: ─ black glass separator ─
 *   Row 3: [ONLINE STAFF] | [BROADCAST] | [ADMIN TOOLS] | [ALERTS] | [EXIT]
 *   Row 4: ─ black glass border ─
 *   Row 5: ─ black glass ─ [FOOTER] ─ black glass ─
 *
 * Custom-model-data icons (PAPER + resource pack):
 *   CMD 1=tickets  2=staff_dashboard  3=server_monitor  4=console_terminal  5=knowledge_base
 *   CMD 6=online_staff  7=broadcast  8=admin_tools  9=alerts  10=exit_btn
 */
public class MainMenuGUI implements InventoryHolder {

    public static final int SIZE = 54;

    // ── Button slots (Row 1 at 9,11,13,15,17 — Row 3 at 27,29,31,33,35) ──────
    public static final int SLOT_TICKETS          = 9;
    public static final int SLOT_STAFF_DASHBOARD  = 11;
    public static final int SLOT_SERVER_MONITOR   = 13;
    public static final int SLOT_CONSOLE_TERMINAL = 15;
    public static final int SLOT_KNOWLEDGE_BASE   = 17;
    public static final int SLOT_ONLINE_STAFF     = 27;
    public static final int SLOT_BROADCAST        = 29;
    public static final int SLOT_ADMIN_TOOLS      = 31;
    public static final int SLOT_ALERTS           = 33;
    public static final int SLOT_EXIT             = 35;

    private static final int SLOT_FOOTER = 49;

    private final ITPlugin  plugin;
    private final Inventory inventory;

    public MainMenuGUI(ITPlugin plugin, Player viewer) {
        this.plugin    = plugin;
        this.inventory = Bukkit.createInventory(this, SIZE, "§9IT CENTER");
        build(viewer);
    }

    private void build(Player viewer) {
        var lp = plugin.getLuckPermsManager();

        // ── Background fills ───────────────────────────────────────────────────
        var bg  = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        var sep = new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build();

        // Rows 0, 2, 4 — full black border row
        for (int s : new int[]{
                0,1,2,3,4,5,6,7,8,
                18,19,20,21,22,23,24,25,26,
                36,37,38,39,40,41,42,43,44})
            inventory.setItem(s, bg);
        // Row 5 — all black
        for (int s = 45; s < 54; s++) inventory.setItem(s, bg);

        // Blue separators between buttons in rows 1 and 3
        for (int s : new int[]{10, 12, 14, 16, 28, 30, 32, 34})
            inventory.setItem(s, sep);

        // ── Row 1 buttons ─────────────────────────────────────────────────────
        int myOpen  = plugin.getTicketManager().countOpenTicketsFor(viewer.getUniqueId());
        int allOpen = plugin.getTicketManager().getOpenTickets().size();
        boolean staff = lp.isStaff(viewer);

        inventory.setItem(SLOT_TICKETS,
                btn(1, "&9Tickets",
                        "&7View & Manage",
                        "&7Support Tickets",
                        "&8§o" + (staff ? allOpen + " open (all)" : myOpen + " open"))
                        .glowing(staff ? allOpen > 0 : myOpen > 0)
                        .build());

        inventory.setItem(SLOT_STAFF_DASHBOARD,
                btn(2, "&9Staff Dashboard",
                        "&7Staff Online &",
                        "&7Ticket Overview",
                        staff ? "&8§oStaff access" : "&c§oStaff only")
                        .build());

        inventory.setItem(SLOT_SERVER_MONITOR,
                btn(3, "&9Server Monitor",
                        "&7Live Stats &",
                        "&7Performance",
                        staff ? "&8§oStaff access" : "&c§oStaff only")
                        .build());

        inventory.setItem(SLOT_CONSOLE_TERMINAL,
                btn(4, "&9Console Terminal",
                        "&7View Logs &",
                        "&7Run Commands",
                        staff ? "&8§oStaff access" : "&c§oStaff only")
                        .build());

        int noteCount = plugin.getNoteManager().getAllNotes().size();
        inventory.setItem(SLOT_KNOWLEDGE_BASE,
                btn(5, "&9Knowledge Base",
                        "&7Browse Articles",
                        "&7& Guides",
                        "&8§o" + noteCount + " article(s)")
                        .build());

        // ── Row 3 buttons ─────────────────────────────────────────────────────
        long onlineStaffCount = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getLuckPermsManager().isStaff(p))
                .count();

        inventory.setItem(SLOT_ONLINE_STAFF,
                btn(6, "&9Online Staff",
                        "&7View Online",
                        "&7Staff Members",
                        "&8§o" + onlineStaffCount + " online")
                        .build());

        inventory.setItem(SLOT_BROADCAST,
                btn(7, "&9Broadcast",
                        "&7Send IT Staff",
                        "&7Broadcasts",
                        lp.isAdmin(viewer) ? "&8§oAdmin access" : "&c§oAdmin only")
                        .build());

        inventory.setItem(SLOT_ADMIN_TOOLS,
                btn(8, "&9Admin Tools",
                        "&7Advanced",
                        "&7Administration",
                        lp.isAdmin(viewer) ? "&8§oAdmin access" : "&c§oAdmin only")
                        .build());

        int errorCount = plugin.getConsoleMonitor().getCapturedErrors().size();
        inventory.setItem(SLOT_ALERTS,
                btn(9, "&cAlerts",
                        "&7View System",
                        "&7Alerts",
                        "&8§o" + errorCount + " error(s) captured")
                        .glowing(errorCount > 0)
                        .build());

        inventory.setItem(SLOT_EXIT,
                btn(10, "&cExit",
                        "&7Close the",
                        "&7IT Center")
                        .build());

        // ── Footer branding ────────────────────────────────────────────────────
        inventory.setItem(SLOT_FOOTER,
                new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE)
                        .name("&8—— &9IT PLUGIN &8——")
                        .lore("&8IT Support · Monitoring · Solutions")
                        .build());
    }

    /**
     * Creates a PAPER ItemBuilder with the given CustomModelData index (1-based)
     * so the resource pack can swap in the correct pixel-art texture.
     */
    private ItemBuilder btn(int cmd, String name, String... loreLines) {
        return new ItemBuilder(Material.PAPER)
                .customModelData(cmd)
                .name(name)
                .lore(loreLines);
    }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }
}
