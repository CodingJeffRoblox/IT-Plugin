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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ticket detail GUI — shows full ticket information + action buttons.
 *
 * Layout (54 slots):
 *   Row 0-1: border
 *   Slot 13: Ticket info item (book)
 *   Slot 29: Close Ticket (staff/owner)
 *   Slot 31: Add Comment
 *   Slot 33: Assign (staff only)
 *   Slot 45: Back to list
 *   Slot 49: Close GUI
 */
public class TicketDetailGUI implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_INFO    = 13;
    public static final int SLOT_CLOSE_TICKET = 29;
    public static final int SLOT_COMMENT = 31;
    public static final int SLOT_ASSIGN  = 33;
    public static final int SLOT_BACK    = 45;
    public static final int SLOT_CLOSE   = 49;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ITPlugin plugin;
    private final Inventory inventory;
    private final Ticket ticket;
    private final List<Ticket> parentList;
    private final int parentPage;
    private final boolean parentShowAll;

    public TicketDetailGUI(ITPlugin plugin, Player viewer, Ticket ticket,
                           List<Ticket> parentList, int parentPage, boolean parentShowAll) {
        this.plugin = plugin;
        this.ticket = ticket;
        this.parentList = parentList;
        this.parentPage = parentPage;
        this.parentShowAll = parentShowAll;

        var mm = plugin.getMessageManager();
        String idPrefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");
        String title = mm.color(mm.get("gui-detail-title", idPrefix + "-" + ticket.getId()));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build(viewer);
    }

    private void build(Player viewer) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        String idPrefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");

        // Border
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, border);
        // Clear center area
        int[] clear = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        for (int s : clear) inventory.setItem(s, null);

        // ── Main info item ──────────────────────────────────────────────
        Material infoMat = switch (ticket.getStatus()) {
            case OPEN -> Material.WRITTEN_BOOK;
            case IN_PROGRESS -> Material.BOOK;
            case CLOSED -> Material.BOOKSHELF;
        };

        List<String> infoLore = new ArrayList<>();
        infoLore.add(mm.get("gui-ticket-status", ticket.getStatus().displayName()));
        infoLore.add(mm.get("gui-ticket-from", ticket.getSubmitterName()));
        String assigneeDisplay = ticket.getAssignee() != null
                ? ticket.getAssignee() : mm.get("gui-ticket-unassigned");
        infoLore.add(mm.get("gui-ticket-assigned", assigneeDisplay));
        infoLore.add(mm.get("gui-ticket-created",
                DATE_FMT.format(Instant.ofEpochSecond(ticket.getCreatedAt()))));
        infoLore.add("");
        infoLore.add(mm.get("gui-ticket-issue", ticket.getMessage()));
        if (!ticket.getComments().isEmpty()) {
            infoLore.add("");
            infoLore.add(mm.get("gui-ticket-comments-header"));
            for (String c : ticket.getComments()) {
                infoLore.add(mm.get("gui-ticket-comment-line", c));
            }
        }

        inventory.setItem(SLOT_INFO, new ItemBuilder(infoMat)
                .name("&f#" + idPrefix + "-" + ticket.getId() + " " + ticket.getStatus().displayName())
                .lore(infoLore)
                .build());

        // ── Action buttons ──────────────────────────────────────────────
        boolean isOwner = viewer.getUniqueId().equals(ticket.getSubmitterUuid());
        boolean staff = lp.isStaff(viewer);
        boolean admin = lp.isAdmin(viewer);
        boolean canClose = isOwner && lp.hasPermission(viewer, "itplugin.ticket.close.own")
                || (staff && lp.hasPermission(viewer, "itplugin.ticket.close.all"))
                || admin;

        if (canClose && ticket.getStatus() != TicketStatus.CLOSED) {
            inventory.setItem(SLOT_CLOSE_TICKET, new ItemBuilder(Material.RED_CONCRETE)
                    .name(mm.get("gui-btn-close-ticket"))
                    .lore(mm.get("gui-lore-close-ticket"))
                    .build());
        }

        inventory.setItem(SLOT_COMMENT, new ItemBuilder(Material.FEATHER)
                .name(mm.get("gui-btn-comment"))
                .lore(mm.get("gui-lore-comment"))
                .build());

        if (staff || admin) {
            inventory.setItem(SLOT_ASSIGN, new ItemBuilder(Material.PLAYER_HEAD)
                    .name(mm.get("gui-btn-assign-ticket"))
                    .lore(mm.get("gui-lore-assign-ticket"))
                    .build());
        }

        // ── Navigation ──────────────────────────────────────────────────
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(mm.get("gui-btn-back"))
                .lore("&7Return to ticket list")
                .build());

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(mm.get("gui-btn-close"))
                .build());
    }

    public Ticket getTicket() { return ticket; }
    public List<Ticket> getParentList() { return parentList; }
    public int getParentPage() { return parentPage; }
    public boolean isParentShowAll() { return parentShowAll; }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }
}
