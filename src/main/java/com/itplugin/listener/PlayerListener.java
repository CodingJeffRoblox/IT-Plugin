package com.itplugin.listener;

import com.itplugin.ITPlugin;
import com.itplugin.ticket.Ticket;
import com.itplugin.ticket.TicketStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class PlayerListener implements Listener {

    private final ITPlugin plugin;

    public PlayerListener(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        // Notify staff of total open ticket count
        if (lp.isStaff(player)) {
            List<Ticket> openTickets = plugin.getTicketManager().getOpenTickets();
            if (!openTickets.isEmpty()) {
                player.sendMessage(mm.get("join-open-tickets-staff", openTickets.size()));
            }
        }

        // Remind the player of their own open tickets
        long myOpen = plugin.getTicketManager()
                .getTicketsFor(player.getUniqueId())
                .stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED)
                .count();
        if (myOpen > 0) {
            player.sendMessage(mm.get("join-own-open-tickets", myOpen));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up any pending GUI chat input when the player disconnects
        plugin.getGUIListener().cancelInput(event.getPlayer());
    }
}
