package com.itplugin.monitor;

import com.itplugin.ITPlugin;
import com.itplugin.util.ChatUtil;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors other servers in the BungeeCord network using the BungeeCord
 * plugin messaging channel.  Sends PlayerCount requests periodically for
 * each configured tracked server and stores the result.
 */
public class ServerMonitor implements PluginMessageListener {

    private final ITPlugin plugin;
    private final List<String> trackedServers;
    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private BukkitTask pollTask;
    private BukkitTask tpsTask;

    // Simple TPS tracker — counts ticks over a 5-second window
    private final AtomicLong tickCount = new AtomicLong(0);
    private volatile double currentTps = 20.0;
    private long tpsWindowStart = System.currentTimeMillis();

    public ServerMonitor(ITPlugin plugin) {
        this.plugin = plugin;
        this.trackedServers = plugin.getConfig().getStringList("server-monitor.tracked-servers");
    }

    public void start() {
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);

        int intervalTicks = plugin.getConfig().getInt("server-monitor.poll-interval-ticks", 200);
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollServers, 40L, intervalTicks);

        // TPS tracker: count every tick, recalculate every 100 ticks (5 seconds)
        tpsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long ticks = tickCount.incrementAndGet();
            if (ticks % 100 == 0) {
                long now = System.currentTimeMillis();
                long elapsed = now - tpsWindowStart;
                currentTps = Math.min(20.0, (100.0 / elapsed) * 1000.0);
                tpsWindowStart = now;
                tickCount.set(0);
            }
        }, 1L, 1L);

        plugin.getLogger().info("Cross-server monitor started. Tracking: " + trackedServers);
    }

    public void stop() {
        if (pollTask != null) pollTask.cancel();
        if (tpsTask != null) tpsTask.cancel();
        Bukkit.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "BungeeCord", this);
        Bukkit.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "BungeeCord");
    }

    private void pollServers() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) return; // BungeeCord messaging requires an online player

        Player proxy = online.iterator().next();

        // Request total player count for each tracked server
        for (String server : trackedServers) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PlayerCount");
            out.writeUTF(server);
            proxy.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }

        // Also request the name of this server so we can label our own entry
        ByteArrayDataOutput out2 = ByteStreams.newDataOutput();
        out2.writeUTF("GetServer");
        proxy.sendPluginMessage(plugin, "BungeeCord", out2.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "PlayerCount" -> {
                String server = in.readUTF();
                int count = in.readInt();
                playerCounts.put(server, count);
                lastSeen.put(server, System.currentTimeMillis());
            }
            case "GetServer" -> {
                String thisServer = in.readUTF();
                int localCount = Bukkit.getOnlinePlayers().size();
                playerCounts.put(thisServer + " (this)", localCount);
                lastSeen.put(thisServer + " (this)", System.currentTimeMillis());
            }
        }
    }

    /** Return a formatted status report for all tracked servers. */
    public List<String> getStatusReport() {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        lines.add(ChatUtil.LINE);
        lines.add(ChatUtil.PREFIX + "§bCross-Server Network Status");
        lines.add(ChatUtil.LINE);

        if (playerCounts.isEmpty()) {
            lines.add("  §7No data yet — waiting for first poll…");
        } else {
            for (Map.Entry<String, Integer> entry : playerCounts.entrySet()) {
                String server = entry.getKey();
                int count = entry.getValue();
                long seenMs = lastSeen.getOrDefault(server, 0L);
                long ageSeconds = (now - seenMs) / 1000;
                String ageStr = seenMs == 0 ? "§cnever" : "§7" + ageSeconds + "s ago";
                lines.add("  §f" + server + " §8— §a" + count + " §7players §8| §7last seen " + ageStr);
            }
        }

        // Local TPS (measured by our own tick counter)
        String tpsColor = currentTps >= 19.5 ? "§a" : currentTps >= 15 ? "§e" : "§c";
        lines.add("  §fThis server TPS: " + tpsColor + String.format("%.2f", currentTps));
        lines.add(ChatUtil.LINE);
        return lines;
    }

    public Map<String, Integer> getPlayerCounts() {
        return Collections.unmodifiableMap(playerCounts);
    }

    /** Returns the locally-measured TPS (used by MonitorClient for heartbeat payloads). */
    public double getCurrentTps() {
        return currentTps;
    }
}
