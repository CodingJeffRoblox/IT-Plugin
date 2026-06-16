package com.itplugin.command;

import com.itplugin.ITPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /ithelp — lists every ITPlugin command with a short description.
 * Available to all players (no permission required beyond being on the server).
 */
public class ITHelpCommand implements CommandExecutor, TabCompleter {

    private final ITPlugin plugin;

    public ITHelpCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        boolean isStaff = (sender instanceof org.bukkit.entity.Player p) && lp.isStaff(p);
        boolean isAdmin = (sender instanceof org.bukkit.entity.Player p2) && lp.isAdmin(p2);
        boolean isConsole = !(sender instanceof org.bukkit.entity.Player);

        line(sender, mm);
        sender.sendMessage(mm.color(mm.get("prefix") + "&bITPlugin Command Reference"));
        line(sender, mm);

        // ── Help Desk ─────────────────────────────────────────────────────────
        sender.sendMessage(mm.color("  &7&l— Help Desk (/ticket)"));
        sender.sendMessage(mm.color("  &f/ticket create          &8— Submit a new IT ticket"));
        sender.sendMessage(mm.color("  &f/ticket list [page]     &8— View your tickets"));
        sender.sendMessage(mm.color("  &f/ticket view <id>       &8— View ticket details"));
        sender.sendMessage(mm.color("  &f/ticket close <id>      &8— Close your ticket"));
        if (isStaff || isAdmin || isConsole) {
            sender.sendMessage(mm.color("  &f/ticket assign <id> <staff> &8— &7Assign a ticket (staff)"));
            sender.sendMessage(mm.color("  &f/ticket comment <id> <msg>  &8— &7Add a comment (staff)"));
        }

        line(sender, mm);

        // ── Knowledge Base ────────────────────────────────────────────────────
        if (isStaff || isAdmin || isConsole) {
            sender.sendMessage(mm.color("  &7&l— Knowledge Base (/itnotes)"));
            sender.sendMessage(mm.color("  &f/itnotes list           &8— List all articles"));
            sender.sendMessage(mm.color("  &f/itnotes view <id>      &8— Read an article"));
            sender.sendMessage(mm.color("  &f/itnotes search <query> &8— Search articles"));
            sender.sendMessage(mm.color("  &f/itnotes add <title>|<body> &8— Add article (staff)"));
            if (isAdmin || isConsole) {
                sender.sendMessage(mm.color("  &f/itnotes delete <id>    &8— Delete an article (admin)"));
            }
            line(sender, mm);
        }

        // ── Console Terminal ──────────────────────────────────────────────────
        if (isAdmin || isConsole) {
            sender.sendMessage(mm.color("  &7&l— Console Terminal (/itconsole)"));
            sender.sendMessage(mm.color("  &f/itconsole              &8— Open terminal GUI"));
            sender.sendMessage(mm.color("  &f/itconsole exec <cmd>   &8— Run a server command"));
            sender.sendMessage(mm.color("  &f/itconsole book         &8— Export log to book"));
            sender.sendMessage(mm.color("  &f/itconsole filter <lvl> &8— Filter by log level"));
            sender.sendMessage(mm.color("  &f/itconsole clear        &8— Clear captured log"));
            line(sender, mm);
        }

        // ── Admin ─────────────────────────────────────────────────────────────
        if (isAdmin || isConsole) {
            sender.sendMessage(mm.color("  &7&l— Admin Panel (/itadmin)"));
            sender.sendMessage(mm.color("  &f/itadmin                &8— Open control panel GUI"));
            sender.sendMessage(mm.color("  &f/itadmin monitor        &8— Cross-server status"));
            sender.sendMessage(mm.color("  &f/itadmin validate       &8— Scan plugin configs"));
            sender.sendMessage(mm.color("  &f/itadmin errors [page]  &8— View console errors"));
            sender.sendMessage(mm.color("  &f/itadmin clearerrors    &8— Clear error log"));
            sender.sendMessage(mm.color("  &f/itadmin reload         &8— Reload config + messages"));
            sender.sendMessage(mm.color("  &7&lTrust Monitor:"));
            sender.sendMessage(mm.color("  &f/itadmin trust generate <nick>          &8— Create token (hub)"));
            sender.sendMessage(mm.color("  &f/itadmin trust connect <url> <tok> <n>  &8— Connect to hub (remote)"));
            sender.sendMessage(mm.color("  &f/itadmin trust list / status / revoke   &8— Manage trusted servers"));
            line(sender, mm);
        }

        // ── Broadcast ─────────────────────────────────────────────────────────
        if (isAdmin || isConsole) {
            sender.sendMessage(mm.color("  &7&l— Broadcast (/itbroadcast)"));
            sender.sendMessage(mm.color("  &f/itbroadcast <message>  &8— Send IT staff announcement"));
            line(sender, mm);
        }

        // ── Aliases ───────────────────────────────────────────────────────────
        sender.sendMessage(mm.color("  &8Aliases: &7/helpdesk&8, &7/hd &8→ /ticket  |  &7/itterm &8→ /itconsole  |  &7/itkb &8→ /itnotes"));
        line(sender, mm);
        return true;
    }

    private void line(CommandSender sender, com.itplugin.manager.MessageManager mm) {
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        return List.of();
    }
}
