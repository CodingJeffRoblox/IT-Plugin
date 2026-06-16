package com.itplugin.monitor;

import com.google.gson.Gson;
import com.itplugin.ITPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends periodic heartbeats to the Plugin Studio cloud API
 * ({@code POST /api/monitor/heartbeat}).
 *
 * Activated when {@code monitor.cloud.enabled=true} in config.yml.
 * Works on any hosting provider — only outbound HTTP is required.
 */
public class CloudMonitorClient {

    private final ITPlugin plugin;
    private BukkitTask    task;
    private final HttpClient http;
    private final Gson    gson = new Gson();
    private final long    enabledAt = System.currentTimeMillis();

    public CloudMonitorClient(ITPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void start() {
        int intervalSec = plugin.getConfig().getInt(
                "monitor.cloud.heartbeat-interval-seconds", 30);
        long ticks = intervalSec * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::sendHeartbeat, 100L, ticks);
        plugin.getLogger().info("[IT Cloud] Client started — heartbeat every " + intervalSec + "s.");
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void sendHeartbeat() {
        String apiUrl = plugin.getConfig().getString("monitor.cloud.api-url", "").strip();
        String apiKey = plugin.getConfig().getString("monitor.cloud.api-key", "").strip();
        String nick   = plugin.getConfig().getString("monitor.cloud.server-nick", "unknown").strip();

        if (apiUrl.isBlank() || nick.isBlank()) return;

        int    players  = Bukkit.getOnlinePlayers().size();
        double tps      = plugin.getServerMonitor() != null
                ? plugin.getServerMonitor().getCurrentTps() : 20.0;
        int    errors   = plugin.getConsoleMonitor() != null
                ? plugin.getConsoleMonitor().getCapturedErrors().size() : 0;
        String version  = Bukkit.getBukkitVersion();
        long   uptime   = (System.currentTimeMillis() - enabledAt) / 1000L;

        String json = gson.toJson(new Payload(nick, players, tps, errors, version, uptime));
        String url  = apiUrl.replaceAll("/+$", "") + "/monitor/heartbeat";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (!apiKey.isBlank()) builder.header("X-IT-Key", apiKey);

            HttpResponse<String> resp = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 403) {
                plugin.getLogger().warning("[IT Cloud] API rejected heartbeat: invalid X-IT-Key.");
            } else if (resp.statusCode() != 200) {
                plugin.getLogger().warning("[IT Cloud] API returned HTTP " + resp.statusCode());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IT Cloud] Heartbeat failed: " + e.getMessage());
        }
    }

    private record Payload(
            String nick,
            int    players,
            double tps,
            int    errors,
            String version,
            long   uptimeSeconds
    ) {}
}
