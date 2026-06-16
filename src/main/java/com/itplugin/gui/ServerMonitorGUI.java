package com.itplugin.gui;

import com.itplugin.ITPlugin;
import com.itplugin.monitor.TrustManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live server-network monitor GUI (54 slots).
 *
 * Layout:
 *   Row 0: glass border + [Hub/Beacon] title at slot 4
 *   Rows 1-4: up to 28 remote server items (glass side-columns)
 *   Row 5: [Back(45)] glass [Refresh(49)] glass [Close(53)]
 *
 * Auto-refreshes every 5 s while open (BukkitTask managed by GUIListener).
 */
public class ServerMonitorGUI implements InventoryHolder {

    public static final int SIZE         = 54;
    public static final int SLOT_HUB     = 4;
    public static final int SLOT_BACK    = 45;
    public static final int SLOT_REFRESH = 49;
    public static final int SLOT_CLOSE   = 53;

    /** Inner area — rows 1-4, cols 1-7 (28 slots for remote servers). */
    private static final int[] SERVER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final ITPlugin plugin;
    private final Inventory inventory;

    public ServerMonitorGUI(ITPlugin plugin) {
        this.plugin = plugin;
        String title = plugin.getMessageManager().color("&9&lIT Network Monitor");
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        buildAll();
    }

    // ── Full build (called once on open) ─────────────────────────────────────

    private void buildAll() {
        // Border
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, border);
        // Clear inner area
        for (int s : SERVER_SLOTS) inventory.setItem(s, null);

        // Static controls (never change on refresh)
        inventory.setItem(SLOT_BACK,
                new ItemBuilder(Material.ARROW)
                        .name("&7← Back to Control Panel")
                        .lore("&7Return to the IT Control Panel")
                        .build());

        inventory.setItem(SLOT_REFRESH,
                new ItemBuilder(Material.CLOCK)
                        .name("&aRefresh")
                        .lore("&7Click to reload server status",
                              "&8(Auto-refreshes every 5 s)")
                        .glowing(true)
                        .build());

        inventory.setItem(SLOT_CLOSE,
                new ItemBuilder(Material.BARRIER)
                        .name("&cClose")
                        .build());

        refresh(); // populate dynamic items immediately
    }

    // ── Refresh — only updates dynamic slots, no flicker ─────────────────────

    public void refresh() {
        long now = System.currentTimeMillis();
        var tm = plugin.getTrustManager();

        // Hub item
        inventory.setItem(SLOT_HUB, buildHubItem(now));

        // Remote servers
        Map<String, TrustManager.HeartbeatSnapshot> hbs = tm.getAllHeartbeats();
        List<String> nicks = new ArrayList<>(tm.getAllTrustedNicks().keySet());

        // Clear all server slots first so removed servers disappear
        for (int s : SERVER_SLOTS) inventory.setItem(s, null);

        if (nicks.isEmpty()) {
            // Center hint
            inventory.setItem(SERVER_SLOTS[13],
                    new ItemBuilder(Material.PAPER)
                            .name("&eNo remote servers registered")
                            .lore("&7On this server run:",
                                  "&f  /itadmin trust generate <nick>",
                                  "&7It will print the connect command to",
                                  "&7run on the remote server.")
                            .build());
        } else {
            int max = Math.min(nicks.size(), SERVER_SLOTS.length);
            for (int i = 0; i < max; i++) {
                String nick = nicks.get(i);
                inventory.setItem(SERVER_SLOTS[i],
                        buildServerItem(nick, hbs.get(nick), now));
            }
            if (nicks.size() > SERVER_SLOTS.length) {
                // Overflow hint in the last slot
                int overflow = nicks.size() - SERVER_SLOTS.length;
                inventory.setItem(SERVER_SLOTS[SERVER_SLOTS.length - 1],
                        new ItemBuilder(Material.PAPER)
                                .name("&e+" + overflow + " more server(s)")
                                .lore("&7(pagination coming soon)")
                                .build());
            }
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildHubItem(long now) {
        int localPlayers = Bukkit.getOnlinePlayers().size();
        double tps = plugin.getServerMonitor() != null
                ? plugin.getServerMonitor().getCurrentTps()
                : 20.0;
        String tpsColor = tps >= 19.5 ? "&a" : tps >= 15 ? "&e" : "&c";

        int totalServers = plugin.getTrustManager().getAllTrustedNicks().size();
        long onlineCount = plugin.getTrustManager().getAllHeartbeats().values().stream()
                .filter(s -> (now - s.lastSeen()) < 60_000)
                .count();

        return new ItemBuilder(Material.BEACON)
                .name("&b&l" + Bukkit.getServer().getName())
                .lore(
                    "&8&m————————————————",
                    "&7Role: &bHub (This Server)",
                    "&7Players: &f" + localPlayers,
                    "&7TPS: " + tpsColor + String.format("%.1f", tps),
                    "&7Status: &aOnline",
                    "&8&m————————————————",
                    "&7Remote servers: &f" + totalServers
                            + " &8(" + onlineCount + " online)",
                    "&8Last refreshed: &7just now"
                )
                .glowing(true)
                .build();
    }

    private ItemStack buildServerItem(String nick, TrustManager.HeartbeatSnapshot snap, long now) {
        if (snap == null) {
            // Registered but never connected
            return new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7" + nick)
                    .lore(
                        "&8&m————————————————",
                        "&8Status: &8● Never connected",
                        "",
                        "&8Connect from that server:",
                        "&8/itadmin trust connect <hub-url> <token> " + nick
                    )
                    .build();
        }

        long ageSec = (now - snap.lastSeen()) / 1000;
        boolean online = ageSec < 60;
        boolean stale  = ageSec < 300;

        Material mat;
        String statusLabel;
        String statusColor;
        if (online) {
            mat = Material.LIME_CONCRETE;
            statusLabel = "● Online";
            statusColor = "&a";
        } else if (stale) {
            mat = Material.YELLOW_CONCRETE;
            statusLabel = "● Stale";
            statusColor = "&e";
        } else {
            mat = Material.RED_CONCRETE;
            statusLabel = "● Offline";
            statusColor = "&c";
        }

        String tpsColor = snap.tps() >= 19.5 ? "&a" : snap.tps() >= 15 ? "&e" : "&c";
        String ageStr = ageSec < 60   ? ageSec + "s ago"
                      : ageSec < 3600 ? (ageSec / 60) + "m ago"
                      :                 (ageSec / 3600) + "h " + ((ageSec % 3600) / 60) + "m ago";

        String errorStr = snap.errors() > 0 ? "&c" + snap.errors() : "&a0";

        return new ItemBuilder(mat)
                .name("&b&l" + nick.substring(0, 1).toUpperCase() + nick.substring(1))
                .lore(
                    "&8&m————————————————",
                    "&7Status:  " + statusColor + statusLabel,
                    "&7Players: &f" + snap.players(),
                    "&7TPS:     " + tpsColor + String.format("%.1f", snap.tps()),
                    "&7Errors:  " + errorStr,
                    "&7Version: &8" + snap.version(),
                    "&8&m————————————————",
                    "&7Last seen: &f" + ageStr,
                    "&7Uptime:   &f" + formatUptime(snap.uptimeSeconds())
                )
                .glowing(online)
                .build();
    }

    private String formatUptime(long seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        return h + "h " + m + "m";
    }

    // ── InventoryHolder ───────────────────────────────────────────────────────

    public void open(Player player) { player.openInventory(inventory); }

    @Override
    public Inventory getInventory() { return inventory; }
}
