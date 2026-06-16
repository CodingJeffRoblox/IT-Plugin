package com.itplugin.command;

import com.itplugin.ITPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /itbroadcast <message> — sends a staff-authored IT broadcast to all online players.
 * Permission: itplugin.admin
 */
public class ITBroadcastCommand implements CommandExecutor, TabCompleter {

    private final ITPlugin plugin;

    public ITBroadcastCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("itplugin.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.getMessageManager().get("broadcast-usage"));
            return true;
        }

        String raw = String.join(" ", args);
        String senderName = (sender instanceof Player) ? sender.getName() : "Console";
        String message = plugin.getMessageManager().get("broadcast-format")
                .replace("{0}", senderName)
                .replace("{1}", raw);

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
        sender.sendMessage(plugin.getMessageManager().get("broadcast-sent")
                .replace("{0}", String.valueOf(Bukkit.getOnlinePlayers().size())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("<message>");
        return Collections.emptyList();
    }
}
