package com.itplugin.listener;

import com.itplugin.ITPlugin;
import com.itplugin.gui.ConsoleTerminalGUI;
import com.itplugin.gui.KnowledgeBaseGUI;
import com.itplugin.gui.MainMenuGUI;
import com.itplugin.gui.ServerMonitorGUI;
import com.itplugin.gui.StaffDashboardGUI;
import com.itplugin.gui.TicketDetailGUI;
import com.itplugin.gui.TicketListGUI;
import com.itplugin.manager.NoteManager;
import com.itplugin.monitor.ConsoleMonitor;
import com.itplugin.monitor.ConsoleReader.LogLevel;
import com.itplugin.ticket.Ticket;
import com.itplugin.ticket.TicketStatus;
import com.itplugin.util.ConfigValidator;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all inventory click events for ITPlugin GUIs, async chat inputs
 * for ticket flows / terminal commands, and the writable-book note submission.
 */
public class GUIListener implements Listener {

    private enum InputMode { CREATE_TICKET, ASSIGN_TICKET, COMMENT_TICKET, EXEC_COMMAND, BROADCAST_MSG }

    private record PendingInput(InputMode mode, int ticketId, List<Ticket> list,
                                int page, boolean showAll, LogLevel termFilter) {}

    private final ITPlugin plugin;
    private final Map<UUID, PendingInput> awaiting    = new ConcurrentHashMap<>();
    /** Players who have been given a WRITABLE_BOOK and are expected to sign it. */
    private final Set<UUID> pendingBookNotes           = ConcurrentHashMap.newKeySet();
    /** Tracks auto-refresh BukkitTask IDs per player for ServerMonitorGUI. */
    private final Map<UUID, BukkitTask>   monitorTask = new ConcurrentHashMap<>();
    private static final DateTimeFormatter AUDIT_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public GUIListener(ITPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Click dispatcher
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;
        var holder = event.getInventory().getHolder();

        if (holder instanceof MainMenuGUI) {
            event.setCancelled(true);
            handleMainMenu(player, event.getRawSlot());
        } else if (holder instanceof TicketListGUI listGUI) {
            event.setCancelled(true);
            handleTicketList(player, listGUI, event.getRawSlot());
        } else if (holder instanceof TicketDetailGUI detailGUI) {
            event.setCancelled(true);
            handleTicketDetail(player, detailGUI, event.getRawSlot());
        } else if (holder instanceof ConsoleTerminalGUI termGUI) {
            event.setCancelled(true);
            handleTerminal(player, termGUI, event.getRawSlot());
        } else if (holder instanceof StaffDashboardGUI) {
            event.setCancelled(true);
            handleDashboard(player, event.getRawSlot());
        } else if (holder instanceof KnowledgeBaseGUI kbGUI) {
            event.setCancelled(true);
            handleKnowledgeBase(player, kbGUI, event.getRawSlot());
        } else if (holder instanceof ServerMonitorGUI monitorGUI) {
            event.setCancelled(true);
            handleServerMonitor(player, monitorGUI, event.getRawSlot());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // InventoryClose — cancel server-monitor refresh task
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof ServerMonitorGUI) {
            BukkitTask task = monitorTask.remove(player.getUniqueId());
            if (task != null) task.cancel();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Book-signing — capture IT note content from signed writable book
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerEditBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (!event.isSigning()) return;
        if (!pendingBookNotes.remove(player.getUniqueId())) return;

        BookMeta meta = event.getNewBookMeta();
        String title = (meta.getTitle() != null && !meta.getTitle().isBlank())
                ? meta.getTitle().trim() : "Untitled Note";

        List<String> pages = meta.getPages();
        String body = pages.isEmpty() ? "(no content)" : String.join("\n\n", pages).trim();

        NoteManager.Note note = plugin.getNoteManager().addNote(title, body, player.getName());
        var mm = plugin.getMessageManager();

        // Remove the resulting WRITTEN_BOOK from inventory on the next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.getInventory().remove(Material.WRITTEN_BOOK);
        });

        player.sendMessage(mm.color("&8&m                                    &r"));
        player.sendMessage(mm.color("&a&l[IT Notes] &fArticle saved successfully!"));
        player.sendMessage(mm.color("  &8ID:    &b#" + note.id()));
        player.sendMessage(mm.color("  &8Title: &f" + title));
        player.sendMessage(mm.color("  &8View:  &7/itnotes view " + note.id()));
        player.sendMessage(mm.color("&8&m                                    &r"));
    }

    /** Register that a player is expected to sign a book to create a note. */
    public void setPendingBookNote(UUID uuid) {
        pendingBookNotes.add(uuid);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Main menu
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleMainMenu(Player player, int slot) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        if (slot == MainMenuGUI.SLOT_EXIT) {
            player.closeInventory();

        } else if (slot == MainMenuGUI.SLOT_TICKETS) {
            // Staff see all open tickets; regular players see their own
            if (lp.isStaff(player)) {
                openTicketList(player, plugin.getTicketManager().getOpenTickets(), 1, false);
            } else {
                openTicketList(player, plugin.getTicketManager().getTicketsFor(player.getUniqueId()), 1, false);
            }

        } else if (slot == MainMenuGUI.SLOT_STAFF_DASHBOARD) {
            if (!lp.isStaff(player)) { player.sendMessage(mm.get("no-permission")); return; }
            new StaffDashboardGUI(plugin, player).open(player);

        } else if (slot == MainMenuGUI.SLOT_SERVER_MONITOR) {
            if (!lp.isStaff(player)) { player.sendMessage(mm.get("no-permission")); return; }
            openServerMonitor(player);

        } else if (slot == MainMenuGUI.SLOT_CONSOLE_TERMINAL) {
            if (!canUseTerminal(player)) { player.sendMessage(mm.get("no-permission")); return; }
            new ConsoleTerminalGUI(plugin, player, LogLevel.ALL, 1).open(player);

        } else if (slot == MainMenuGUI.SLOT_KNOWLEDGE_BASE) {
            if (!lp.isStaff(player)) { player.sendMessage(mm.get("no-permission")); return; }
            new KnowledgeBaseGUI(plugin, player).open(player);

        } else if (slot == MainMenuGUI.SLOT_ONLINE_STAFF) {
            if (!lp.isStaff(player)) { player.sendMessage(mm.get("no-permission")); return; }
            player.closeInventory();
            showOnlineStaff(player);

        } else if (slot == MainMenuGUI.SLOT_BROADCAST) {
            if (!lp.isAdmin(player)) { player.sendMessage(mm.get("no-permission")); return; }
            player.closeInventory();
            awaiting.put(player.getUniqueId(),
                    new PendingInput(InputMode.BROADCAST_MSG, -1, null, 0, false, null));
            player.sendMessage(mm.get("gui-broadcast-prompt"));

        } else if (slot == MainMenuGUI.SLOT_ADMIN_TOOLS) {
            if (!lp.isAdmin(player)) { player.sendMessage(mm.get("no-permission")); return; }
            player.closeInventory();
            runValidate(player);

        } else if (slot == MainMenuGUI.SLOT_ALERTS) {
            if (!lp.isAdmin(player)) { player.sendMessage(mm.get("no-permission")); return; }
            player.closeInventory();
            showErrors(player, 1);
        }
    }

    /** Show online staff members in chat. */
    private void showOnlineStaff(Player player) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        var online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> lp.isStaff(p))
                .toList();
        player.sendMessage(mm.color("&8&m        &r &9&l Online Staff &8&m        "));
        if (online.isEmpty()) {
            player.sendMessage(mm.color("  &7No staff members currently online."));
        } else {
            for (Player s : online) {
                String rank = lp.isAdmin(s) ? "&c[Admin]" : "&b[Staff]";
                player.sendMessage(mm.color("  " + rank + " &f" + s.getName()));
            }
        }
        player.sendMessage(mm.color("  &8Total: &b" + online.size() + " staff online"));
        player.sendMessage(mm.color("&8&m                                    &r"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Staff Dashboard
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleDashboard(Player player, int slot) {
        var lp = plugin.getLuckPermsManager();

        if (slot == StaffDashboardGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == StaffDashboardGUI.SLOT_OPEN_TICKETS) {
            openTicketList(player, plugin.getTicketManager().getOpenTickets(), 1, false);
        } else if (slot == StaffDashboardGUI.SLOT_ERRORS && lp.isAdmin(player)) {
            player.closeInventory();
            showErrors(player, 1);
        } else if (slot == StaffDashboardGUI.SLOT_NOTES) {
            new KnowledgeBaseGUI(plugin, player).open(player);
        } else if (slot == StaffDashboardGUI.SLOT_VALIDATE && lp.isAdmin(player)) {
            player.closeInventory();
            runValidate(player);
        } else if (slot == StaffDashboardGUI.SLOT_KB) {
            new KnowledgeBaseGUI(plugin, player).open(player);
        } else if (slot == StaffDashboardGUI.SLOT_MONITOR) {
            player.closeInventory();
            plugin.getServerMonitor().getStatusReport().forEach(player::sendMessage);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Knowledge Base
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleKnowledgeBase(Player player, KnowledgeBaseGUI gui, int slot) {
        var mm    = plugin.getMessageManager();
        boolean admin = plugin.getLuckPermsManager().isAdmin(player);
        boolean staff = plugin.getLuckPermsManager().isStaff(player);

        if (slot == KnowledgeBaseGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == KnowledgeBaseGUI.SLOT_PREV && gui.getPage() > 0) {
            new KnowledgeBaseGUI(plugin, player, gui.getPage() - 1).open(player);
        } else if (slot == KnowledgeBaseGUI.SLOT_NEXT) {
            int totalPages = Math.max(1, (int) Math.ceil(
                    gui.getNotes().size() / (double) KnowledgeBaseGUI.NOTES_PER_PAGE));
            if (gui.getPage() < totalPages - 1) {
                new KnowledgeBaseGUI(plugin, player, gui.getPage() + 1).open(player);
            }
        } else if (slot == KnowledgeBaseGUI.SLOT_ADD && (staff || admin)) {
            player.closeInventory();
            openBookEditorForPlayer(player);
        } else {
            // Resolve slot → note index
            int noteIdx = resolveNoteSlot(slot, gui.getPage());
            if (noteIdx >= 0 && noteIdx < gui.getNotes().size()) {
                NoteManager.Note note = gui.getNotes().get(noteIdx);
                player.closeInventory();
                player.sendMessage(mm.color("&8&m          &r &b[Note #" + note.id() + "] &f" + note.title() + " &8&m          "));
                player.sendMessage(mm.color("&7" + note.body()));
                player.sendMessage(mm.color(""));
                player.sendMessage(mm.color("&8Author: &b" + note.author()));
                if (admin) {
                    player.sendMessage(mm.color("&8To delete: &c/itnotes delete " + note.id()));
                }
            }
        }
    }

    /** Map an inventory slot back to a note list index for the current page. */
    private int resolveNoteSlot(int slot, int page) {
        for (int i = 0; i < KnowledgeBaseGUI.NOTE_SLOTS.length; i++) {
            if (KnowledgeBaseGUI.NOTE_SLOTS[i] == slot) {
                return page * KnowledgeBaseGUI.NOTES_PER_PAGE + i;
            }
        }
        return -1;
    }

    /** Give the player a blank writable book and register them as pending a note. */
    private void openBookEditorForPlayer(Player player) {
        var mm = plugin.getMessageManager();

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta  = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("IT Article");
            meta.addPage("Write your article here.\n\nThe book title (set when you sign)\nbecomes the note title.\nAll pages become the note body.");
            book.setItemMeta(meta);
        }

        player.getInventory().addItem(book);
        player.updateInventory();
        pendingBookNotes.add(player.getUniqueId());

        player.sendMessage(mm.color("&8&m                                    &r"));
        player.sendMessage(mm.color("&b&l[IT Notes] &fA writable book has been added to your inventory."));
        player.sendMessage(mm.color("&7  1. &fRight-click &7the book to open the editor."));
        player.sendMessage(mm.color("&7  2. Write your content across the pages."));
        player.sendMessage(mm.color("&7  3. Click &fSign &7— the title you enter becomes the article title."));
        player.sendMessage(mm.color("&8&m                                    &r"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ticket list
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleTicketList(Player player, TicketListGUI gui, int slot) {
        if (slot == TicketListGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == TicketListGUI.SLOT_BACK) {
            new MainMenuGUI(plugin, player).open(player);
        } else if (slot == TicketListGUI.SLOT_PREV && gui.getPage() > 1) {
            openTicketList(player, gui.getTickets(), gui.getPage() - 1, gui.isShowAll());
        } else if (slot == TicketListGUI.SLOT_NEXT && gui.getPage() < gui.getTotalPages()) {
            openTicketList(player, gui.getTickets(), gui.getPage() + 1, gui.isShowAll());
        } else {
            Ticket t = gui.getTicketAt(slot);
            if (t != null) {
                boolean isOwner = player.getUniqueId().equals(t.getSubmitterUuid());
                boolean canView = isOwner || plugin.getLuckPermsManager().isStaff(player);
                if (canView) {
                    new TicketDetailGUI(plugin, player, t,
                            gui.getTickets(), gui.getPage(), gui.isShowAll()).open(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().get("no-permission"));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ticket detail
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleTicketDetail(Player player, TicketDetailGUI gui, int slot) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        Ticket ticket = gui.getTicket();

        if (slot == TicketDetailGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == TicketDetailGUI.SLOT_BACK) {
            openTicketList(player, gui.getParentList(), gui.getParentPage(), gui.isParentShowAll());
        } else if (slot == TicketDetailGUI.SLOT_CLOSE_TICKET) {
            boolean isOwner = player.getUniqueId().equals(ticket.getSubmitterUuid());
            boolean canClose = (isOwner && lp.hasPermission(player, "itplugin.ticket.close.own"))
                    || (lp.isStaff(player) && lp.hasPermission(player, "itplugin.ticket.close.all"))
                    || lp.isAdmin(player);
            if (!canClose) { player.sendMessage(mm.get("no-permission")); return; }
            if (ticket.getStatus() == TicketStatus.CLOSED) {
                player.sendMessage(mm.get("ticket-already-closed",
                        plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + ticket.getId()));
                return;
            }
            plugin.getTicketManager().closeTicket(ticket.getId());
            String id = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + ticket.getId();
            player.sendMessage(mm.get("ticket-closed", id));
            Player submitter = Bukkit.getPlayer(ticket.getSubmitterUuid());
            if (submitter != null && !submitter.equals(player)) {
                submitter.sendMessage(mm.get("ticket-closed-notify", id, player.getName()));
            }
            openTicketList(player, gui.getParentList(), gui.getParentPage(), gui.isParentShowAll());
        } else if (slot == TicketDetailGUI.SLOT_COMMENT) {
            player.closeInventory();
            awaiting.put(player.getUniqueId(),
                    new PendingInput(InputMode.COMMENT_TICKET, ticket.getId(),
                            gui.getParentList(), gui.getParentPage(), gui.isParentShowAll(), null));
            player.sendMessage(mm.get("ticket-prompt-comment",
                    plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + ticket.getId()));
        } else if (slot == TicketDetailGUI.SLOT_ASSIGN && lp.isStaff(player)) {
            player.closeInventory();
            awaiting.put(player.getUniqueId(),
                    new PendingInput(InputMode.ASSIGN_TICKET, ticket.getId(),
                            gui.getParentList(), gui.getParentPage(), gui.isParentShowAll(), null));
            player.sendMessage(mm.get("ticket-prompt-assign",
                    plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + ticket.getId()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Terminal GUI
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleTerminal(Player player, ConsoleTerminalGUI gui, int slot) {
        var mm = plugin.getMessageManager();

        if (slot == ConsoleTerminalGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == ConsoleTerminalGUI.SLOT_BACK) {
            new MainMenuGUI(plugin, player).open(player);
        } else if (slot == ConsoleTerminalGUI.SLOT_BOOK) {
            player.closeInventory();
            ConsoleTerminalGUI.openLogBook(plugin, player, gui.getFilter());
        } else if (slot == ConsoleTerminalGUI.SLOT_RUN_CMD) {
            player.closeInventory();
            awaiting.put(player.getUniqueId(),
                    new PendingInput(InputMode.EXEC_COMMAND, -1, null, gui.getPage(), false, gui.getFilter()));
            player.sendMessage(mm.get("terminal-prompt-command"));
        } else if (slot == ConsoleTerminalGUI.SLOT_PREV && gui.getPage() > 1) {
            new ConsoleTerminalGUI(plugin, player, gui.getFilter(), gui.getPage() - 1).open(player);
        } else if (slot == ConsoleTerminalGUI.SLOT_NEXT && gui.getPage() < gui.getTotalPages()) {
            new ConsoleTerminalGUI(plugin, player, gui.getFilter(), gui.getPage() + 1).open(player);
        } else if (slot == ConsoleTerminalGUI.SLOT_FILTER) {
            LogLevel next = switch (gui.getFilter()) {
                case ALL     -> LogLevel.INFO;
                case INFO    -> LogLevel.WARNING;
                case WARNING -> LogLevel.SEVERE;
                case SEVERE  -> LogLevel.ALL;
            };
            new ConsoleTerminalGUI(plugin, player, next, 1).open(player);
        } else if (slot == ConsoleTerminalGUI.SLOT_CLEAR) {
            plugin.getConsoleReader().clear();
            player.sendMessage(mm.get("terminal-cleared"));
            new ConsoleTerminalGUI(plugin, player, gui.getFilter(), 1).open(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Chat input (async)
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = awaiting.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();
        var mm = plugin.getMessageManager();

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(mm.get("ticket-prompt-cancelled"));
            if (pending.mode() == InputMode.EXEC_COMMAND) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ConsoleTerminalGUI(plugin, player, pending.termFilter(), pending.page()).open(player));
            }
            return;
        }

        final PendingInput fin = pending;
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (fin.mode()) {
                case CREATE_TICKET -> {
                    int max = plugin.getConfig().getInt("helpdesk.max-open-tickets", 3);
                    if (plugin.getTicketManager().countOpenTicketsFor(player.getUniqueId()) >= max) {
                        player.sendMessage(mm.get("ticket-max-open", max));
                        return;
                    }
                    Ticket t = plugin.getTicketManager().createTicket(
                            player.getName(), player.getUniqueId(), input);
                    String id = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + t.getId();
                    player.sendMessage(mm.get("ticket-created", t.getId()));
                    if (plugin.getConfig().getBoolean("helpdesk.notify-staff-on-create", true)) {
                        String alert = mm.get("ticket-new-staff-alert", id, player.getName(),
                                input.length() > 60 ? input.substring(0, 60) + "…" : input);
                        Bukkit.getOnlinePlayers().stream()
                                .filter(p -> plugin.getLuckPermsManager().isStaff(p) && !p.equals(player))
                                .forEach(p -> p.sendMessage(alert));
                    }
                    new MainMenuGUI(plugin, player).open(player);
                }
                case COMMENT_TICKET -> {
                    plugin.getTicketManager().addComment(fin.ticketId(), player.getName(), input);
                    String ticketId = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + fin.ticketId();
                    player.sendMessage(mm.get("ticket-comment-added", ticketId));
                    plugin.getTicketManager().getTicket(fin.ticketId()).ifPresent(t -> {
                        if (!player.getUniqueId().equals(t.getSubmitterUuid())) {
                            Player sub = Bukkit.getPlayer(t.getSubmitterUuid());
                            if (sub != null) sub.sendMessage(mm.get("ticket-comment-notify", ticketId, input));
                        }
                    });
                    plugin.getTicketManager().getTicket(fin.ticketId()).ifPresent(t ->
                            new TicketDetailGUI(plugin, player, t,
                                    fin.list(), fin.page(), fin.showAll()).open(player));
                }
                case ASSIGN_TICKET -> {
                    plugin.getTicketManager().assignTicket(fin.ticketId(), input);
                    String ticketId = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + fin.ticketId();
                    player.sendMessage(mm.get("ticket-assigned", ticketId, input));
                    Player assignee = Bukkit.getPlayer(input);
                    if (assignee != null) {
                        plugin.getTicketManager().getTicket(fin.ticketId()).ifPresent(t ->
                                assignee.sendMessage(mm.get("ticket-assigned-notify", ticketId, t.getMessage())));
                    }
                    plugin.getTicketManager().getTicket(fin.ticketId()).ifPresent(t ->
                            new TicketDetailGUI(plugin, player, t,
                                    fin.list(), fin.page(), fin.showAll()).open(player));
                }
                case EXEC_COMMAND -> executeConsoleCommandInternal(player, input, fin.termFilter());
                case BROADCAST_MSG -> {
                    String formatted = mm.get("broadcast-format", player.getName(), input);
                    Bukkit.broadcastMessage(formatted);
                    int count = Bukkit.getOnlinePlayers().size();
                    player.sendMessage(mm.get("broadcast-sent", count));
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Console command execution (called from ITConsoleCommand and GUI)
    // ═══════════════════════════════════════════════════════════════════════════

    public void executeConsoleCommand(Player player, String command, LogLevel termFilter) {
        executeConsoleCommandInternal(player, command, termFilter);
    }

    private void executeConsoleCommandInternal(Player player, String command, LogLevel termFilter) {
        var mm = plugin.getMessageManager();

        List<String> blocked = plugin.getConfig().getStringList("console-terminal.blocked-commands");
        String cmdLower = command.toLowerCase().trim();
        for (String b : blocked) {
            if (cmdLower.startsWith(b.toLowerCase())) {
                player.sendMessage(mm.get("terminal-command-denied", command));
                new ConsoleTerminalGUI(plugin, player, termFilter != null ? termFilter : LogLevel.ALL, 1).open(player);
                return;
            }
        }

        if (plugin.getConfig().getBoolean("console-terminal.log-commands", true)) {
            auditLog(player.getName(), command);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        player.sendMessage(mm.get("terminal-command-executed", command));

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                new ConsoleTerminalGUI(plugin, player, termFilter != null ? termFilter : LogLevel.ALL, 1).open(player), 5L);
    }

    private void auditLog(String playerName, String command) {
        String logPath = plugin.getConfig().getString(
                "console-terminal.command-log-file", "plugins/ITPlugin/commands.log");
        File f = new File(logPath);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
            pw.println("[" + LocalDateTime.now().format(AUDIT_FMT) + "] " + playerName + " → " + command);
        } catch (IOException ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean canUseTerminal(Player player) {
        return plugin.getLuckPermsManager().isAdmin(player)
                || plugin.getLuckPermsManager().hasPermission(player, "itplugin.console");
    }

    private void openTicketList(Player player, List<Ticket> tickets, int page, boolean showAll) {
        new TicketListGUI(plugin, player, tickets, page, showAll).open(player);
    }

    private void runValidate(Player player) {
        var mm = plugin.getMessageManager();
        player.sendMessage(mm.get("validate-scanning"));
        List<String> dirs = plugin.getConfig().getStringList("config-validator.scan-directories");
        boolean rw = plugin.getConfig().getBoolean("config-validator.report-warnings", true);
        int total = 0;
        for (String d : dirs) {
            for (var r : ConfigValidator.validatePluginConfigs(new File(d), rw)) {
                for (String err : r.errors()) {
                    player.sendMessage(mm.color("&c[ERR]  &f" + r.file().getName() + "&8: &7" + err));
                    total++;
                }
                for (String w : r.warnings()) {
                    player.sendMessage(mm.color("&e[WARN] &f" + r.file().getName() + "&8: &7" + w));
                    total++;
                }
            }
        }
        player.sendMessage(total == 0 ? mm.get("validate-clean") : mm.get("validate-issues", total));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error display with click-to-copy
    // ═══════════════════════════════════════════════════════════════════════════

    public void showErrors(Player player, int page) {
        var mm = plugin.getMessageManager();
        List<ConsoleMonitor.ErrorEntry> entries = plugin.getConsoleMonitor().getCapturedEntries();
        if (entries.isEmpty()) { player.sendMessage(mm.get("admin-errors-none")); return; }

        int pageSize   = 8;
        int totalPages = (int) Math.ceil(entries.size() / (double) pageSize);
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * pageSize;
        int end   = Math.min(start + pageSize, entries.size());

        player.sendMessage(mm.color("&8&m                              &r"));
        player.sendMessage(mm.color("&c⚠ Console Errors &8(page " + page + "/" + totalPages + ", " + entries.size() + " total)"));

        for (int i = start; i < end; i++) {
            ConsoleMonitor.ErrorEntry e = entries.get(i);
            String levelColor = "SEVERE".equals(e.level()) ? "§c" : "§e";
            String snippet    = e.message().length() > 65 ? e.message().substring(0, 65) + "…" : e.message();
            String displayRaw = "  §8[" + (i + 1) + "] " + levelColor + "[" + e.serverName() + "] §7[" + e.source() + "] §f" + snippet;

            // Build a clickable component (COPY_TO_CLIPBOARD) with hover preview
            TextComponent line = new TextComponent(displayRaw);
            line.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, e.fullText()));
            String hoverPreview = (e.fullText().length() > 200 ? e.fullText().substring(0, 200) + "…" : e.fullText())
                    + "\n§8Click to copy to clipboard";
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(hoverPreview)
                            .color(net.md_5.bungee.api.ChatColor.GRAY).create()));
            player.spigot().sendMessage(line);
        }

        if (page < totalPages) {
            player.sendMessage(mm.color("  &8Next: &7/itadmin errors " + (page + 1)));
        }
        player.sendMessage(mm.color("  &8&o✦ Hover an entry to preview, click to copy."));
        player.sendMessage(mm.color("&8&m                              &r"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Server Monitor GUI
    // ═══════════════════════════════════════════════════════════════════════════

    public void openServerMonitor(Player player) {
        BukkitTask old = monitorTask.remove(player.getUniqueId());
        if (old != null) old.cancel();

        ServerMonitorGUI gui = new ServerMonitorGUI(plugin);
        gui.open(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, gui::refresh, 100L, 100L);
        monitorTask.put(player.getUniqueId(), task);
    }

    private void handleServerMonitor(Player player, ServerMonitorGUI gui, int slot) {
        if (slot == ServerMonitorGUI.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == ServerMonitorGUI.SLOT_REFRESH) {
            gui.refresh();
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getMessageManager().get("prefix") + "&7Server status refreshed."));
        } else if (slot == ServerMonitorGUI.SLOT_BACK) {
            player.closeInventory();
            new MainMenuGUI(plugin, player).open(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cleanup on disconnect
    // ═══════════════════════════════════════════════════════════════════════════

    public void cancelInput(Player player) {
        awaiting.remove(player.getUniqueId());
        pendingBookNotes.remove(player.getUniqueId());
    }
}
