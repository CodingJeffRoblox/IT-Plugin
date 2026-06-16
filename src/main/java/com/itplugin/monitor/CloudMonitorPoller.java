package com.itplugin.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itplugin.ITPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Runs on the hub server — periodically polls the Plugin Studio cloud API
 * ({@code GET /api/monitor/servers}) and injects the results into
 * {@link TrustManager} so the in-game Server Monitor GUI has fresh data.
 *
 * Activated automatically when {@code monitor.cloud.enabled=true}.
 */
public class CloudMonitorPoller {

    private final ITPlugin plugin;
    private BukkitTask     task;
    private final HttpClient http;
    private final Gson     gson = new Gson();

    public CloudMonitorPoller(ITPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void start() {
        int intervalSec = plugin.getConfig().getInt(
                "monitor.cloud.poll-interval-seconds", 15);
        long ticks = intervalSec * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::poll, 120L, ticks);
        plugin.getLogger().info("[IT Cloud] Poller started — checking server states every " + intervalSec + "s.");
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void poll() {
        String apiUrl = plugin.getConfig().getString("monitor.cloud.api-url", "").strip();
        String apiKey = plugin.getConfig().getString("monitor.cloud.api-key", "").strip();
        if (apiUrl.isBlank()) return;

        String url = apiUrl.replaceAll("/+$", "") + "/monitor/servers";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            if (!apiKey.isBlank()) builder.header("X-IT-Key", apiKey);

            HttpResponse<String> resp = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("[IT Cloud] Poll returned HTTP " + resp.statusCode());
                return;
            }

            JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
            TrustManager tm = plugin.getTrustManager();

            for (String key : root.keySet()) {
                JsonObject entry = root.getAsJsonObject(key);
                String  nick    = getStr(entry, "nick", key);
                int     players = getInt(entry, "players", 0);
                double  tps     = getDbl(entry, "tps", 20.0);
                int     errors  = getInt(entry, "errors", 0);
                String  version = getStr(entry, "version", "unknown");
                long    uptime  = getLng(entry, "uptimeSeconds", 0L);
                long    lastSeen= getLng(entry, "lastSeen", System.currentTimeMillis());

                // Auto-register the server if it's new
                tm.registerCloud(nick);

                TrustManager.HeartbeatSnapshot snap = new TrustManager.HeartbeatSnapshot(
                        nick, players, tps, errors, version, uptime, lastSeen);

                // Inject on main thread (TrustManager map is not concurrent)
                Bukkit.getScheduler().runTask(plugin,
                        () -> tm.updateHeartbeat(nick.toLowerCase(), snap));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[IT Cloud] Poll error: " + e.getMessage());
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String getStr(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : def;
    }

    private int getInt(JsonObject o, String k, int def) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsInt() : def;
    }

    private double getDbl(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsDouble() : def;
    }

    private long getLng(JsonObject o, String k, long def) {
        JsonElement e = o.get(k);
        return (e != null && !e.isJsonNull()) ? e.getAsLong() : def;
    }
}
