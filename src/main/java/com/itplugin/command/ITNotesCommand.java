package com.itplugin.command;

import com.itplugin.ITPlugin;
import com.itplugin.gui.KnowledgeBaseGUI;
import com.itplugin.manager.NoteManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /itnotes — IT knowledge base notes.
 *
 *   /itnotes                     → open GUI (players) or list (console)
 *   /itnotes list [page]         → list notes
 *   /itnotes view <id>           → view note body
 *   /itnotes add                 → opens a writable book GUI (players)
 *   /itnotes add <title>|<body>  → inline add (console / fallback)
 *   /itnotes delete <id>         → delete note (admin)
 *   /itnotes search <query>      → search notes
 */
public class ITNotesCommand implements CommandExecutor, TabCompleter {

    private final ITPlugin plugin;

    public ITNotesCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var mm = plugin.getMessageManager();

        if (!sender.hasPermission("itplugin.staff") && !sender.hasPermission("itplugin.admin")) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }

        NoteManager nm = plugin.getNoteManager();

        // No args → open GUI for players, list for console
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new KnowledgeBaseGUI(plugin, player).open(player);
            } else {
                printList(sender, 1);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                int page = args.length > 1 ? parseIntOrOne(args[1]) : 1;
                printList(sender, page);
            }
            case "view" -> {
                if (args.length < 2) { sender.sendMessage(mm.get("notes-usage-view")); return true; }
                int id = parseIntOrOne(args[1]);
                NoteManager.Note note = nm.getNote(id);
                if (note == null) { sender.sendMessage(mm.get("notes-not-found").replace("{0}", args[1])); return true; }
                sender.sendMessage(mm.color("&8&m         &r &b[Note #" + note.id() + "] &f" + note.title() + " &8&m         "));
                sender.sendMessage(mm.color("&7" + note.body()));
                sender.sendMessage(mm.color("&8Author: &7" + note.author()));
            }
            case "add" -> {
                if (!sender.hasPermission("itplugin.staff")) { sender.sendMessage(mm.get("no-permission")); return true; }

                if (sender instanceof Player player && args.length == 1) {
                    // Open writable book GUI for in-game add
                    openBookEditor(player);
                    return true;
                }

                // Console or inline: /itnotes add <title>|<body>
                if (args.length < 2) {
                    if (sender instanceof Player) {
                        // Player typed /itnotes add with no extra args — open book
                        openBookEditor((Player) sender);
                    } else {
                        sender.sendMessage(mm.get("notes-usage-add"));
                    }
                    return true;
                }
                String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String[] parts = joined.split("\\|", 2);
                if (parts.length < 2) { sender.sendMessage(mm.get("notes-usage-add")); return true; }
                String title  = parts[0].trim();
                String body   = parts[1].trim();
                String author = (sender instanceof Player p) ? p.getName() : "Console";
                NoteManager.Note note = nm.addNote(title, body, author);
                sender.sendMessage(mm.get("notes-added").replace("{0}", String.valueOf(note.id())).replace("{1}", title));
            }
            case "delete" -> {
                if (!sender.hasPermission("itplugin.admin")) { sender.sendMessage(mm.get("no-permission")); return true; }
                if (args.length < 2) { sender.sendMessage(mm.get("notes-usage-delete")); return true; }
                int id = parseIntOrOne(args[1]);
                if (nm.deleteNote(id)) {
                    sender.sendMessage(mm.get("notes-deleted").replace("{0}", args[1]));
                } else {
                    sender.sendMessage(mm.get("notes-not-found").replace("{0}", args[1]));
                }
            }
            case "search" -> {
                if (args.length < 2) { sender.sendMessage(mm.get("notes-usage-search")); return true; }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                var results = nm.searchNotes(query);
                if (results.isEmpty()) {
                    sender.sendMessage(mm.get("notes-search-none").replace("{0}", query));
                } else {
                    sender.sendMessage(mm.color("&bSearch results for \"&f" + query + "&b\":"));
                    results.forEach(n -> sender.sendMessage(mm.color("  &8#" + n.id() + " &f" + n.title() + " &8— &7" + n.author())));
                }
            }
            default -> sender.sendMessage(mm.get("notes-usage"));
        }
        return true;
    }

    /**
     * Gives the player a blank writable book in their off-hand slot area,
     * registers them as pending a book-note submission, and instructs them.
     */
    private void openBookEditor(Player player) {
        var mm = plugin.getMessageManager();

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("IT Article");
            meta.addPage("Write your article here.\n\nThe book title (set when you sign) becomes the note title.\nAll pages become the note body.");
            book.setItemMeta(meta);
        }

        // Add to inventory; prefer off-hand, then first empty slot, then drop
        player.getInventory().addItem(book);
        player.updateInventory();

        plugin.getGUIListener().setPendingBookNote(player.getUniqueId());

        player.sendMessage(mm.color("&8&m                                    &r"));
        player.sendMessage(mm.color("&b&l[IT Notes] &fA writable book has been added to your inventory."));
        player.sendMessage(mm.color("&7  1. &fRight-click &7the book to start writing."));
        player.sendMessage(mm.color("&7  2. Enter your article content across the pages."));
        player.sendMessage(mm.color("&7  3. Click &fSign &7— the title you enter becomes the article title."));
        player.sendMessage(mm.color("&7  4. The note will be saved automatically."));
        player.sendMessage(mm.color("&8&m                                    &r"));
    }

    private void printList(CommandSender sender, int page) {
        var mm  = plugin.getMessageManager();
        var all = plugin.getNoteManager().getAllNotes();
        int perPage    = 8;
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        sender.sendMessage(mm.color("&b=== IT Notes (Page " + page + "/" + totalPages + ") ==="));
        if (all.isEmpty()) {
            sender.sendMessage(mm.color("&7No notes yet. Use &f/itnotes add &7or click Add in the GUI."));
            return;
        }
        int start = (page - 1) * perPage;
        int end   = Math.min(start + perPage, all.size());
        for (NoteManager.Note n : all.subList(start, end)) {
            sender.sendMessage(mm.color("  &8#" + n.id() + " &f" + n.title() + " &8— &7" + n.author()));
        }
    }

    private int parseIntOrOne(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("list", "view", "add", "delete", "search");
        return Collections.emptyList();
    }
}
