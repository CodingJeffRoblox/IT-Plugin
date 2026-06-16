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
 * Ticket list GUI — shows up to PAGE_SIZE tickets per page.
 * Slots 0-44: ticket items
 * Slot 45: Back
 * Slot 48: Prev page
 * Slot 49: Page indicator
 * Slot 50: Next page
 * Slot 53: Close
 */
public class TicketListGUI implements InventoryHolder {

    public static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;

    public static final int SLOT_BACK  = 45;
    public static final int SLOT_PREV  = 48;
    public static final int SLOT_INFO  = 49;
    public static final int SLOT_NEXT  = 50;
    public static final int SLOT_CLOSE = 53;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ITPlugin plugin;
    private final Inventory inventory;
    private final List<Ticket> tickets;
    private final int page;
    private final int totalPages;
    private final boolean showAll; // true = incl. closed

    public TicketListGUI(ITPlugin plugin, Player viewer, List<Ticket> tickets, int page, boolean showAll) {
        this.plugin = plugin;
        this.tickets = tickets;
        this.page = page;
        this.totalPages = Math.max(1, (int) Math.ceil(tickets.size() / (double) PAGE_SIZE));
        this.showAll = showAll;

        var mm = plugin.getMessageManager();
        String title = mm.color(mm.get("gui-tickets-title"));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build(viewer);
    }

    private void build(Player viewer) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        String idPrefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");

        // Fill bottom bar with border
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);

        // Ticket items
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tickets.size());
        for (int i = start; i < end; i++) {
            Ticket t = tickets.get(i);
            Material mat = switch (t.getStatus()) {
                case OPEN -> Material.LIME_DYE;
                case IN_PROGRESS -> Material.YELLOW_DYE;
                case CLOSED -> Material.RED_DYE;
            };

            List<String> lore = new ArrayList<>();
            lore.add(mm.get("gui-ticket-status", t.getStatus().displayName()));
            lore.add(mm.get("gui-ticket-from", t.getSubmitterName()));
            if (t.getAssignee() != null) {
                lore.add(mm.get("gui-ticket-assigned", t.getAssignee()));
            } else {
                lore.add(mm.get("gui-ticket-assigned", mm.get("gui-ticket-unassigned")));
            }
            lore.add(mm.get("gui-ticket-created", DATE_FMT.format(Instant.ofEpochSecond(t.getCreatedAt()))));
            lore.add("");
            lore.add(mm.get("gui-ticket-issue", truncate(t.getMessage(), 40)));
            lore.add("");
            lore.add("&7Click to view details");

            inventory.setItem(i - start, new ItemBuilder(mat)
                    .name("&f#" + idPrefix + "-" + t.getId() + " " + t.getStatus().displayName())
                    .lore(lore)
                    .build());
        }

        // Nav buttons
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(mm.get("gui-btn-back"))
                .lore("&7Return to main menu")
                .build());

        if (page > 1) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name(mm.get("gui-btn-prev"))
                    .build());
        }

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(mm.get("gui-btn-page-info", page, totalPages))
                .lore("&7Showing &f" + tickets.size() + "&7 ticket(s)")
                .build());

        if (page < totalPages) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name(mm.get("gui-btn-next"))
                    .build());
        }

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(mm.get("gui-btn-close"))
                .build());
    }

    /** Return the Ticket at the clicked slot, or null if not a ticket slot. */
    public Ticket getTicketAt(int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) return null;
        int idx = (page - 1) * PAGE_SIZE + slot;
        if (idx >= tickets.size()) return null;
        return tickets.get(idx);
    }

    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
    public boolean isShowAll() { return showAll; }
    public List<Ticket> getTickets() { return tickets; }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
