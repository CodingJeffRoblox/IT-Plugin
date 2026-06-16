package com.itplugin.command;

import com.itplugin.ITPlugin;
import com.itplugin.gui.MainMenuGUI;
import com.itplugin.gui.TicketDetailGUI;
import com.itplugin.gui.TicketListGUI;
import com.itplugin.ticket.Ticket;
import com.itplugin.ticket.TicketStatus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * /ticket [create|list|view|close|assign|comment|gui] [args]
 *
 * No args → opens the GUI (players only). Console users get a text summary.
 */
public class HelpDeskCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ITPlugin plugin;

    public HelpDeskCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        // No args → open GUI for players, text help for console
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new MainMenuGUI(plugin, player).open(player);
            } else {
                sendTextHelp(sender);
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "gui", "menu", "open" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(mm.get("player-only"));
                    yield true;
                }
                new MainMenuGUI(plugin, player).open(player);
                yield true;
            }
            case "create", "new" -> cmdCreate(sender, args);
            case "list", "ls" -> cmdList(sender, args);
            case "view", "info" -> cmdView(sender, args);
            case "close", "resolve" -> cmdClose(sender, args);
            case "assign" -> cmdAssign(sender, args);
            case "comment", "reply" -> cmdComment(sender, args);
            default -> { sendTextHelp(sender); yield true; }
        };
    }

    // ─── /ticket create <message…> ───────────────────────────────────────────

    private boolean cmdCreate(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.get("player-only"));
            return true;
        }
        if (!lp.hasPermission(player, "itplugin.ticket.create")) {
            player.sendMessage(mm.get("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(mm.get("ticket-prompt-create"));
            plugin.getGUIListener().cancelInput(player); // clear any existing
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    plugin.getGUIListener().cancelInput(player), 600L); // 30s timeout
            // Register manual chat input
            player.sendMessage(mm.color("&7Or use: &f/ticket create <your issue here>"));
            return true;
        }

        int max = plugin.getConfig().getInt("helpdesk.max-open-tickets", 3);
        if (plugin.getTicketManager().countOpenTicketsFor(player.getUniqueId()) >= max) {
            player.sendMessage(mm.get("ticket-max-open", max));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Ticket ticket = plugin.getTicketManager().createTicket(player.getName(), player.getUniqueId(), message);
        player.sendMessage(mm.get("ticket-created", ticket.getId()));

        notifyStaff(player, ticket, message);
        return true;
    }

    // ─── /ticket list [all] ──────────────────────────────────────────────────

    private boolean cmdList(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        String prefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");
        boolean isStaff = sender instanceof Player p && (lp.isStaff(p) || lp.isAdmin(p));

        List<Ticket> tickets;
        boolean showAll = args.length >= 2 && args[1].equalsIgnoreCase("all");
        if (isStaff && showAll) {
            tickets = plugin.getTicketManager().getAllTickets();
        } else if (isStaff) {
            tickets = plugin.getTicketManager().getOpenTickets();
        } else if (sender instanceof Player player) {
            tickets = plugin.getTicketManager().getTicketsFor(player.getUniqueId());
        } else {
            tickets = plugin.getTicketManager().getOpenTickets();
        }

        // Players get GUI, console gets text
        if (sender instanceof Player player) {
            new TicketListGUI(plugin, player, tickets, 1, showAll).open(player);
            return true;
        }

        if (tickets.isEmpty()) {
            sender.sendMessage(mm.get("ticket-no-results"));
            return true;
        }
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bTickets (" + tickets.size() + ")"));
        for (Ticket t : tickets) {
            String ass = t.getAssignee() != null ? " → " + t.getAssignee() : "";
            sender.sendMessage("  #" + prefix + "-" + t.getId()
                    + " [" + t.getStatus().name() + "]" + ass
                    + " | " + t.getSubmitterName()
                    + " | " + (t.getMessage().length() > 40 ? t.getMessage().substring(0, 40) + "…" : t.getMessage()));
        }
        return true;
    }

    // ─── /ticket view <id> ───────────────────────────────────────────────────

    private boolean cmdView(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        if (args.length < 2) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "&cUsage: /ticket view <id>"));
            return true;
        }
        Optional<Ticket> opt = parseId(sender, args[1]);
        if (opt.isEmpty()) return true;
        Ticket t = opt.get();

        boolean isOwner = sender instanceof Player p && t.getSubmitterUuid().equals(p.getUniqueId());
        boolean canView = isOwner || (sender instanceof Player p2 && lp.isStaff(p2)) || !(sender instanceof Player);
        if (!canView) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }

        if (sender instanceof Player player) {
            new TicketDetailGUI(plugin, player, t,
                    plugin.getTicketManager().getAllTickets(), 1, true).open(player);
            return true;
        }

        String idPrefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");
        sender.sendMessage("=== #" + idPrefix + "-" + t.getId() + " ===");
        sender.sendMessage("Status:   " + t.getStatus().name());
        sender.sendMessage("From:     " + t.getSubmitterName());
        sender.sendMessage("Assigned: " + (t.getAssignee() != null ? t.getAssignee() : "none"));
        sender.sendMessage("Created:  " + DATE_FMT.format(Instant.ofEpochSecond(t.getCreatedAt())));
        sender.sendMessage("Issue:    " + t.getMessage());
        if (!t.getComments().isEmpty()) {
            sender.sendMessage("Comments:");
            t.getComments().forEach(c -> sender.sendMessage("  » " + c));
        }
        return true;
    }

    // ─── /ticket close <id> ──────────────────────────────────────────────────

    private boolean cmdClose(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        if (args.length < 2) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "&cUsage: /ticket close <id>"));
            return true;
        }
        Optional<Ticket> opt = parseId(sender, args[1]);
        if (opt.isEmpty()) return true;
        Ticket t = opt.get();

        boolean isOwner = sender instanceof Player p && t.getSubmitterUuid().equals(p.getUniqueId());
        boolean canClose = (isOwner && (!(sender instanceof Player p2) || lp.hasPermission(p2, "itplugin.ticket.close.own")))
                || (sender instanceof Player p3 && (lp.hasPermission(p3, "itplugin.ticket.close.all") || lp.isAdmin(p3)))
                || !(sender instanceof Player);
        if (!canClose) { sender.sendMessage(mm.get("no-permission")); return true; }

        String idStr = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + t.getId();
        if (t.getStatus() == TicketStatus.CLOSED) {
            sender.sendMessage(mm.get("ticket-already-closed", idStr));
            return true;
        }
        plugin.getTicketManager().closeTicket(t.getId());
        sender.sendMessage(mm.get("ticket-closed", idStr));

        Player submitter = Bukkit.getPlayer(t.getSubmitterUuid());
        if (submitter != null && !submitter.equals(sender)) {
            submitter.sendMessage(mm.get("ticket-closed-notify", idStr, sender.getName()));
        }
        return true;
    }

    // ─── /ticket assign <id> <player> ────────────────────────────────────────

    private boolean cmdAssign(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        if (sender instanceof Player p && !lp.hasPermission(p, "itplugin.ticket.assign") && !lp.isAdmin(p)) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "&cUsage: /ticket assign <id> <staff>"));
            return true;
        }
        Optional<Ticket> opt = parseId(sender, args[1]);
        if (opt.isEmpty()) return true;
        Ticket t = opt.get();
        String staffName = args[2];
        plugin.getTicketManager().assignTicket(t.getId(), staffName);
        String id = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + t.getId();
        sender.sendMessage(mm.get("ticket-assigned", id, staffName));

        Player staff = Bukkit.getPlayer(staffName);
        if (staff != null) staff.sendMessage(mm.get("ticket-assigned-notify", id, t.getMessage()));
        return true;
    }

    // ─── /ticket comment <id> <message…> ─────────────────────────────────────

    private boolean cmdComment(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        if (args.length < 3) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "&cUsage: /ticket comment <id> <message>"));
            return true;
        }
        Optional<Ticket> opt = parseId(sender, args[1]);
        if (opt.isEmpty()) return true;
        Ticket t = opt.get();

        boolean isOwner = sender instanceof Player p && t.getSubmitterUuid().equals(p.getUniqueId());
        boolean isStaff = sender instanceof Player p2 && lp.isStaff(p2);
        if (!isOwner && !isStaff && sender instanceof Player) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }

        String comment = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getTicketManager().addComment(t.getId(), sender.getName(), comment);
        String id = plugin.getConfig().getString("helpdesk.id-prefix", "TICK") + "-" + t.getId();
        sender.sendMessage(mm.get("ticket-comment-added", id));

        if (isStaff) {
            Player submitter = Bukkit.getPlayer(t.getSubmitterUuid());
            if (submitter != null && !submitter.equals(sender)) {
                submitter.sendMessage(mm.get("ticket-comment-notify", id, comment));
            }
        }
        return true;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Optional<Ticket> parseId(CommandSender sender, String raw) {
        var mm = plugin.getMessageManager();
        try {
            int id = Integer.parseInt(raw);
            Optional<Ticket> opt = plugin.getTicketManager().getTicket(id);
            if (opt.isEmpty()) sender.sendMessage(mm.get("ticket-not-found", id));
            return opt;
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.get("invalid-id", raw));
            return Optional.empty();
        }
    }

    private void notifyStaff(Player submitter, Ticket ticket, String message) {
        if (!plugin.getConfig().getBoolean("helpdesk.notify-staff-on-create", true)) return;
        var mm = plugin.getMessageManager();
        String idPrefix = plugin.getConfig().getString("helpdesk.id-prefix", "TICK");
        String id = idPrefix + "-" + ticket.getId();
        String truncMsg = message.length() > 60 ? message.substring(0, 60) + "…" : message;
        String alert = mm.get("ticket-new-staff-alert", id, submitter.getName(), truncMsg);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getLuckPermsManager().isStaff(p) && !p.equals(submitter))
                .forEach(p -> p.sendMessage(alert));
    }

    private void sendTextHelp(CommandSender sender) {
        var mm = plugin.getMessageManager();
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bIT Help Desk"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color("  &f/ticket          &8— Open the IT control panel GUI"));
        sender.sendMessage(mm.color("  &f/ticket create   &8— Open a new ticket (chat prompt)"));
        sender.sendMessage(mm.color("  &f/ticket list     &8— List tickets"));
        sender.sendMessage(mm.color("  &f/ticket view <id>  &8— View a ticket"));
        sender.sendMessage(mm.color("  &f/ticket close <id> &8— Close a ticket"));
        sender.sendMessage(mm.color("  &f/ticket comment <id> <msg> &8— Add a comment"));
        sender.sendMessage(mm.color("  &f/ticket assign <id> <player> &8— Assign a ticket"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("gui", "create", "list", "view", "close", "comment", "assign")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
