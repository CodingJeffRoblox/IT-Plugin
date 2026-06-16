package com.itplugin.command;

import com.itplugin.ITPlugin;
import com.itplugin.gui.ConsoleTerminalGUI;
import com.itplugin.monitor.ConsoleReader.LogLevel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * /itconsole [exec <command> | book [filter] | clear | filter <level>]
 * /itterm (alias)
 *
 * No args (player) → opens the ConsoleTerminalGUI.
 * No args (console) → prints a short status.
 */
public class ITConsoleCommand implements CommandExecutor, TabCompleter {

    private final ITPlugin plugin;

    public ITConsoleCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        // Permission check
        boolean hasAccess = !(sender instanceof Player p)
                || lp.isAdmin(p)
                || lp.hasPermission(p, "itplugin.console");
        if (!hasAccess) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }

        // No args — open GUI or print status
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new ConsoleTerminalGUI(plugin, player, LogLevel.ALL, 1).open(player);
            } else {
                int lines = plugin.getConsoleReader().size();
                sender.sendMessage("[ITPlugin] Console Reader: " + lines + " line(s) captured.");
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "exec", "run", "x" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.color(mm.get("prefix-error") + "&cUsage: /itconsole exec <command>"));
                    yield true;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (sender instanceof Player player) {
                    plugin.getGUIListener().executeConsoleCommand(player, cmd, LogLevel.ALL);
                } else {
                    // Console user running it directly — just dispatch
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
                }
                yield true;
            }
            case "book" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("[ITPlugin] Book view is player-only.");
                    yield true;
                }
                LogLevel filter = parseFilter(args.length >= 2 ? args[1] : "all");
                ConsoleTerminalGUI.openLogBook(plugin, player, filter);
                yield true;
            }
            case "clear" -> {
                plugin.getConsoleReader().clear();
                sender.sendMessage(mm.get("terminal-cleared"));
                yield true;
            }
            case "filter" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("[ITPlugin] Filter is GUI-only.");
                    yield true;
                }
                LogLevel filter = parseFilter(args.length >= 2 ? args[1] : "all");
                new ConsoleTerminalGUI(plugin, player, filter, 1).open(player);
                yield true;
            }
            case "gui", "open" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("[ITPlugin] GUI is player-only.");
                    yield true;
                }
                new ConsoleTerminalGUI(plugin, player, LogLevel.ALL, 1).open(player);
                yield true;
            }
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private LogLevel parseFilter(String s) {
        return switch (s.toUpperCase()) {
            case "INFO"    -> LogLevel.INFO;
            case "WARNING", "WARN" -> LogLevel.WARNING;
            case "SEVERE", "ERROR" -> LogLevel.SEVERE;
            default -> LogLevel.ALL;
        };
    }

    private void sendHelp(CommandSender sender) {
        var mm = plugin.getMessageManager();
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bIT Terminal — Commands"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color("  &f/itconsole            &8— Open terminal GUI"));
        sender.sendMessage(mm.color("  &f/itconsole exec <cmd> &8— Run command as server console"));
        sender.sendMessage(mm.color("  &f/itconsole book [lvl] &8— Open log book (all/info/warn/severe)"));
        sender.sendMessage(mm.color("  &f/itconsole filter <lvl> &8— Open terminal with filter"));
        sender.sendMessage(mm.color("  &f/itconsole clear      &8— Clear captured log"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("exec", "book", "clear", "filter", "gui")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("filter") || args[0].equalsIgnoreCase("book"))) {
            return List.of("all", "info", "warn", "severe")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
