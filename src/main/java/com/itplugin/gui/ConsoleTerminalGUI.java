package com.itplugin.gui;

import com.itplugin.ITPlugin;
import com.itplugin.monitor.ConsoleReader;
import com.itplugin.monitor.ConsoleReader.LogLevel;
import com.itplugin.monitor.ConsoleReader.LogLine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game IT Terminal GUI — live console viewer + command runner.
 *
 * Layout (54 slots):
 *   Row 0 (0-8):   Info bar (server state, filter indicator)
 *   Rows 1-4 (9-44): 36 log line slots
 *   Row 5 (45-53): Navigation / action buttons
 *
 * Controls:
 *   45  Back to main menu
 *   46  Open Log Book (paginated full log)
 *   47  Run Command (chat prompt → executes as console)
 *   48  Prev page
 *   49  Page info
 *   50  Next page
 *   51  Cycle filter level (ALL / INFO / WARN / SEVERE)
 *   52  Clear log
 *   53  Close GUI
 */
public class ConsoleTerminalGUI implements InventoryHolder {

    public static final int SIZE = 36 + 18; // 54

    // Bottom row slots
    public static final int SLOT_BACK        = 45;
    public static final int SLOT_BOOK        = 46;
    public static final int SLOT_RUN_CMD     = 47;
    public static final int SLOT_PREV        = 48;
    public static final int SLOT_PAGE_INFO   = 49;
    public static final int SLOT_NEXT        = 50;
    public static final int SLOT_FILTER      = 51;
    public static final int SLOT_CLEAR       = 52;
    public static final int SLOT_CLOSE       = 53;

    private static final int LOG_SLOTS      = 36; // slots 9-44
    private static final int LOG_SLOT_START = 9;
    private static final int SUMMARY_LENGTH = 45;

    private final ITPlugin plugin;
    private final Inventory inventory;
    private final LogLevel filter;
    private final int page;
    private final int totalPages;
    private final List<LogLine> filteredLines;

    public ConsoleTerminalGUI(ITPlugin plugin, Player viewer, LogLevel filter, int page) {
        this.plugin = plugin;
        this.filter = filter;
        this.filteredLines = plugin.getConsoleReader().getLines(filter);
        this.totalPages = Math.max(1, (int) Math.ceil(filteredLines.size() / (double) LOG_SLOTS));
        this.page = Math.max(1, Math.min(page, totalPages));

        var mm = plugin.getMessageManager();
        String title = mm.color(mm.get("gui-console-title"));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        build(viewer);
    }

    private void build(Player viewer) {
        var mm = plugin.getMessageManager();

        // ── Top info bar ─────────────────────────────────────────────────────
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);

        // Server info item (slot 4, centre of top row)
        int online = Bukkit.getOnlinePlayers().size();
        int maxP = Bukkit.getMaxPlayers();
        String version = Bukkit.getBukkitVersion();
        inventory.setItem(4, new ItemBuilder(Material.COMMAND_BLOCK)
                .name(mm.color("&8[&bServer&8]"))
                .lore("&7Version: &f" + version,
                      "&7Players: &f" + online + "&7/&f" + maxP,
                      "&7Total log lines: &f" + plugin.getConsoleReader().size(),
                      "&7Showing filter: &f" + filterLabel())
                .build());

        // ── Log lines (rows 1-4, slots 9-44) ─────────────────────────────────
        // Clear all log slots first
        for (int i = LOG_SLOT_START; i < LOG_SLOT_START + LOG_SLOTS; i++) {
            inventory.setItem(i, null);
        }

        if (filteredLines.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.STRUCTURE_VOID)
                    .name(mm.get("gui-console-empty"))
                    .build());
        } else {
            int start = (this.page - 1) * LOG_SLOTS;
            int end = Math.min(start + LOG_SLOTS, filteredLines.size());
            for (int i = start; i < end; i++) {
                LogLine line = filteredLines.get(i);
                Material mat = logMaterial(line);
                List<String> lore = splitIntoLore(line.full(), 50);
                inventory.setItem(LOG_SLOT_START + (i - start),
                        new ItemBuilder(mat)
                                .name(line.summary(SUMMARY_LENGTH))
                                .lore(lore)
                                .build());
            }
        }

        // ── Bottom control bar (slots 45-53) ─────────────────────────────────
        ItemStack bottomBorder = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, bottomBorder);

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name(mm.get("gui-btn-back"))
                .lore("&7Return to main menu")
                .build());

        inventory.setItem(SLOT_BOOK, new ItemBuilder(Material.WRITTEN_BOOK)
                .name(mm.get("gui-btn-open-book"))
                .lore("&7View full log in a scrollable book")
                .build());

        inventory.setItem(SLOT_RUN_CMD, new ItemBuilder(Material.COMMAND_BLOCK)
                .name(mm.get("gui-btn-run-command"))
                .lore("&7Execute a command as the server console",
                      "&8(An audit log is kept)")
                .glowing(true)
                .build());

        if (this.page > 1) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name(mm.get("gui-btn-prev"))
                    .build());
        }

        inventory.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(mm.get("gui-btn-page-info", this.page, totalPages))
                .lore("&7Showing &f" + filteredLines.size() + "&7 line(s)")
                .build());

        if (this.page < totalPages) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name(mm.get("gui-btn-next"))
                    .build());
        }

        inventory.setItem(SLOT_FILTER, new ItemBuilder(Material.HOPPER)
                .name(mm.color("&7Filter: &f" + filterLabel()))
                .lore("&7Current: &f" + filterLabel(),
                      "&8Click to cycle: ALL → INFO → WARN → SEVERE")
                .build());

        inventory.setItem(SLOT_CLEAR, new ItemBuilder(Material.LAVA_BUCKET)
                .name(mm.get("gui-btn-clear-log"))
                .lore("&7Wipes all captured log lines")
                .build());

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name(mm.get("gui-btn-close"))
                .build());
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public LogLevel getFilter() { return filter; }
    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }

    // ─── Book builder ─────────────────────────────────────────────────────────

    /**
     * Build and open a written-book view of the current filtered log lines.
     * Each book page holds ~14 lines of about 40 chars each.
     */
    public static void openLogBook(ITPlugin plugin, Player player, LogLevel filter) {
        var mm = plugin.getMessageManager();
        List<LogLine> lines = plugin.getConsoleReader().getLines(filter);

        if (lines.isEmpty()) {
            player.sendMessage(mm.get("terminal-book-no-data"));
            return;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.setTitle("IT Console");
        meta.setAuthor("ITPlugin");

        // Build pages (last lines first — most recent at the front)
        List<String> pages = new ArrayList<>();
        // Show most recent N*14 lines (books have a 100-page limit)
        int maxPages = 80;
        int linesPerPage = 12;
        List<LogLine> recent = lines.subList(
                Math.max(0, lines.size() - maxPages * linesPerPage), lines.size());

        StringBuilder pageText = new StringBuilder();
        int lineCount = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            LogLine l = recent.get(i);
            String entry = l.colorCode() + "[" + l.formattedTime() + "] "
                    + truncate(l.message(), 32) + "\n";
            if (lineCount >= linesPerPage) {
                pages.add(0, pageText.toString()); // newest pages first
                pageText = new StringBuilder();
                lineCount = 0;
            }
            pageText.insert(0, entry);
            lineCount++;
        }
        if (pageText.length() > 0) pages.add(0, pageText.toString());

        if (pages.isEmpty()) {
            player.sendMessage(mm.get("terminal-book-no-data"));
            return;
        }

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String filterLabel() {
        return filter == null ? "ALL" : filter.name();
    }

    private Material logMaterial(LogLine line) {
        int level = line.level().intValue();
        if (level >= java.util.logging.Level.SEVERE.intValue())  return Material.RED_DYE;
        if (level >= java.util.logging.Level.WARNING.intValue()) return Material.YELLOW_DYE;
        return Material.PAPER;
    }

    private List<String> splitIntoLore(String text, int width) {
        List<String> lore = new ArrayList<>();
        while (text.length() > width) {
            lore.add("§7" + text.substring(0, width));
            text = text.substring(width);
        }
        if (!text.isEmpty()) lore.add("§7" + text);
        return lore;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
