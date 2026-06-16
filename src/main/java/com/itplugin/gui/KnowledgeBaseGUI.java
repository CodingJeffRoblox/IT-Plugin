package com.itplugin.gui;

import com.itplugin.ITPlugin;
import com.itplugin.manager.NoteManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ╔══════════════════════════════════════════╗
 * ║   IT Knowledge Base  —  Custom Chest GUI ║
 * ╚══════════════════════════════════════════╝
 *
 * 54-slot layout:
 *   Row 0  (0-8):   Blue glass border + enchanted-book title centre (slot 4)
 *   Rows 1-4 (9-44): Side borders (light-blue) + 28 note slots (7 cols × 4 rows)
 *   Row 5 (45-53):  Navigation bar
 *
 * Note slots:  10-16, 19-25, 28-34, 37-43   (28 notes per page)
 * Nav bar:
 *   45 PREV   46 spacer   47 PAGE-INFO   48 spacer   49 CLOSE
 *   50 spacer   51 ADD-NOTE   52 spacer   53 NEXT
 */
public class KnowledgeBaseGUI implements InventoryHolder {

    public static final int SIZE           = 54;
    public static final int NOTES_PER_PAGE = 28;

    // Navigation slots
    public static final int SLOT_PREV  = 45;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_ADD   = 51;
    public static final int SLOT_NEXT  = 53;

    // Note content slots (7 columns × 4 rows, skipping left+right borders)
    public static final int[] NOTE_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final ITPlugin plugin;
    private final Inventory inventory;
    private final int page;
    private final List<NoteManager.Note> notes;

    public KnowledgeBaseGUI(ITPlugin plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public KnowledgeBaseGUI(ITPlugin plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.notes  = new ArrayList<>(plugin.getNoteManager().getAllNotes());
        this.page   = Math.max(0, page);
        var mm      = plugin.getMessageManager();
        int totalPages = Math.max(1, (int) Math.ceil(notes.size() / (double) NOTES_PER_PAGE));
        String title = mm.color("&9&lIT Knowledge Base  &8" + (this.page + 1) + "/" + totalPages);
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build(viewer);
    }

    private void build(Player viewer) {
        var mm    = plugin.getMessageManager();
        boolean admin = viewer.hasPermission("itplugin.admin");
        boolean staff = viewer.hasPermission("itplugin.staff");

        // ── Row 0: Header border (blue glass) ──────────────────────────────
        ItemStack blueBorder = new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inventory.setItem(i, blueBorder);

        // Centre of header: enchanted-book title item
        inventory.setItem(4,
            new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(mm.color("&b&lIT Knowledge Base"))
                .lore(
                    mm.color("&8━━━━━━━━━━━━━━━━━━━━━━━━"),
                    mm.color("&7Manage and browse IT articles"),
                    mm.color("&7for this server network."),
                    mm.color("&8━━━━━━━━━━━━━━━━━━━━━━━━"),
                    mm.color("&f" + notes.size() + " &7article(s) stored"),
                    mm.color(""),
                    mm.color("&9&oCreated by &b&oCodingJeff")
                )
                .glowing(true)
                .build());

        // ── Rows 1-4: Side borders (light-blue glass) ──────────────────────
        ItemStack sideBorder = new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).name(" ").build();
        for (int row = 1; row <= 4; row++) {
            int base = row * 9;
            inventory.setItem(base,     sideBorder); // left
            inventory.setItem(base + 8, sideBorder); // right
        }

        // ── Note items ──────────────────────────────────────────────────────
        int start = page * NOTES_PER_PAGE;
        int end   = Math.min(start + NOTES_PER_PAGE, notes.size());

        for (int i = start; i < end; i++) {
            NoteManager.Note n = notes.get(i);
            int slotIdx = i - start;
            if (slotIdx >= NOTE_SLOTS.length) break;

            List<String> lore = new ArrayList<>();
            lore.add(mm.color("&8━━━━━━━━━━━━━━━━━━━━━━"));
            String snippet = n.body().length() > 50 ? n.body().substring(0, 50) + "…" : n.body();
            lore.add(mm.color("&7" + snippet));
            lore.add(mm.color(""));
            lore.add(mm.color("&8Author:  &b" + n.author()));
            lore.add(mm.color("&8ID:      &7#" + n.id()));
            lore.add(mm.color(""));
            lore.add(mm.color("&a&oLeft-click &7to read full article"));
            if (admin) {
                lore.add(mm.color("&c&o/itnotes delete " + n.id() + " &8to remove"));
            }

            inventory.setItem(NOTE_SLOTS[slotIdx],
                new ItemBuilder(Material.WRITTEN_BOOK)
                    .name(mm.color("&f&l" + n.title()))
                    .lore(lore.toArray(new String[0]))
                    .build());
        }

        // ── Row 5: Navigation bar ───────────────────────────────────────────
        ItemStack navSpacer = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();

        // Slots 46, 48, 50, 52 = decorative spacers
        inventory.setItem(46, navSpacer);
        inventory.setItem(48, navSpacer);
        inventory.setItem(50, navSpacer);
        inventory.setItem(52, navSpacer);

        // Slot 45: PREV
        int totalPages = Math.max(1, (int) Math.ceil(notes.size() / (double) NOTES_PER_PAGE));
        if (page > 0) {
            inventory.setItem(SLOT_PREV,
                new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name(mm.color("&b« Previous Page"))
                    .lore(mm.color("&7Page " + page + " of " + totalPages))
                    .build());
        } else {
            inventory.setItem(SLOT_PREV,
                new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());
        }

        // Slot 47: Page info
        inventory.setItem(47,
            new ItemBuilder(Material.PAPER)
                .name(mm.color("&bPage &f" + (page + 1) + " &8/ &f" + totalPages))
                .lore(mm.color("&7" + notes.size() + " article(s) total"))
                .build());

        // Slot 49: CLOSE
        inventory.setItem(SLOT_CLOSE,
            new ItemBuilder(Material.BARRIER)
                .name(mm.color("&c&lClose"))
                .lore(mm.color("&7Return to the main menu"))
                .build());

        // Slot 51: ADD NOTE (staff+)
        if (staff || admin) {
            inventory.setItem(SLOT_ADD,
                new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(mm.color("&a&l+ New Article"))
                    .lore(
                        mm.color("&7Click to open a writable book."),
                        mm.color("&7Write your article, sign the"),
                        mm.color("&7book, and it will be saved."),
                        mm.color(""),
                        mm.color("&8The book title becomes the note"),
                        mm.color("&8title; pages become the body.")
                    )
                    .glowing(true)
                    .build());
        } else {
            inventory.setItem(SLOT_ADD, navSpacer);
        }

        // Slot 53: NEXT
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT,
                new ItemBuilder(Material.SPECTRAL_ARROW)
                    .name(mm.color("&bNext Page »"))
                    .lore(mm.color("&7Page " + (page + 2) + " of " + totalPages))
                    .build());
        } else {
            inventory.setItem(SLOT_NEXT,
                new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());
        }
    }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }

    public int getPage()                    { return page; }
    public List<NoteManager.Note> getNotes(){ return notes; }

    // Slot accessors for GUIListener
    public static int slotPrev()  { return SLOT_PREV;  }
    public static int slotClose() { return SLOT_CLOSE; }
    public static int slotAdd()   { return SLOT_ADD;   }
    public static int slotNext()  { return SLOT_NEXT;  }
}
