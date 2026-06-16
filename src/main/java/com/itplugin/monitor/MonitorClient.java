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
 * Runs on a remote server and sends periodic heartbeat payloads to the hub
 * server's {@link MonitorHttpServer} endpoint.
 *
 * Activated when this server has been configured with
 * {@code /itadmin trust connect <hub-url> <token> <nick>} and
 * {@code monitor.client.enabled = true} is in config.yml.
 */
public class MonitorClient {

    private final ITPlugin plugin;
    private BukkitTask task;
    private final HttpClient http;
    private final Gson gson = new Gson();

    // Tracks when ITPlugin was enabled (for uptime reporting)
    private final long enabledAt = System.currentTimeMillis();

    public MonitorClient(ITPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Start the periodic heartbeat loop. */
    public void start() {
        int intervalSeconds = plugin.getConfig().getInt(
                "monitor.client.heartbeat-interval-seconds", 30);
        long intervalTicks = intervalSeconds * 20L;

        // First beat after 5 seconds, then every interval
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendHeartbeat, 100L, intervalTicks);
        plugin.getLogger().info("IT Monitor client started — sending heartbeats every "
                + intervalSeconds + "s.");
    }

    /** Stop the heartbeat loop. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void sendHeartbeat() {
        String mainServer = plugin.getConfig().getString("monitor.client.main-server", "");
        String token      = plugin.getConfig().getString("monitor.client.token", "");
        String serverName = plugin.getConfig().getString("monitor.client.server-name", "unknown");

        if (mainServer.isBlank() || token.isBlank()) return;

        // Gather stats (we're on async thread, some Bukkit calls are main-thread-safe)
        int players  = Bukkit.getOnlinePlayers().size();   // thread-safe read
        double tps   = plugin.getServerMonitor() != null
                ? plugin.getServerMonitor().getCurrentTps() : 20.0;
        int errors   = plugin.getConsoleMonitor() != null
                ? plugin.getConsoleMonitor().getCapturedErrors().size() : 0;
        String version = Bukkit.getBukkitVersion();
        long uptime  = (System.currentTimeMillis() - enabledAt) / 1000L;

        String json = gson.toJson(new HeartbeatPayload(
                token, serverName, players, tps, errors, version, uptime));

        String url = mainServer.replaceAll("/+$", "") + "/itplugin/heartbeat";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403) {
                plugin.getLogger().warning("[IT Monitor] Hub rejected heartbeat: invalid or revoked token. "
                        + "Re-run /itadmin trust generate on the hub.");
            } else if (response.statusCode() != 200) {
                plugin.getLogger().warning("[IT Monitor] Hub returned HTTP " + response.statusCode());
            }
        } catch (java.net.ConnectException e) {
            plugin.getLogger().warning("[IT Monitor] Could not reach hub at " + url + ": " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("[IT Monitor] Heartbeat error: " + e.getMessage());
        }
    }

    // ── Payload ───────────────────────────────────────────────────────────────

    private record HeartbeatPayload(
            String token,
            String name,
            int players,
            double tps,
            int errors,
            String version,
            long uptimeSeconds
    ) {}
}
