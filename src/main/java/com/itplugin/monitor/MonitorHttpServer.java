package com.itplugin.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itplugin.ITPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight embedded HTTP server that runs on this Minecraft server and
 * accepts signed heartbeat payloads from trusted remote ITPlugin instances.
 *
 * Endpoints:
 *   GET  /itplugin/ping           — health-check (used by /itadmin trust connect)
 *   POST /itplugin/heartbeat      — receives JSON heartbeat, validates trust token
 *
 * Start with {@link #start()} after plugin enable; stop with {@link #stop()}.
 */
public class MonitorHttpServer {

    private final ITPlugin plugin;
    private HttpServer server;
    private final Gson gson = new Gson();

    public MonitorHttpServer(ITPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        int port = plugin.getConfig().getInt("monitor.http.port", 28080);
        server = HttpServer.create(new InetSocketAddress(port), 10);
        server.createContext("/itplugin/ping", this::handlePing);
        server.createContext("/itplugin/heartbeat", this::handleHeartbeat);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ITPlugin-MonitorHTTP");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        plugin.getLogger().info("IT Monitor HTTP server started on port " + port
                + ". Trusted servers can now connect.");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("IT Monitor HTTP server stopped.");
        }
    }

    public int getPort() {
        return plugin.getConfig().getInt("monitor.http.port", 28080);
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handlePing(HttpExchange ex) throws IOException {
        respond(ex, 200, "{\"ok\":true,\"plugin\":\"ITPlugin\"}");
    }

    private void handleHeartbeat(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        HeartbeatPayload payload;
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            payload = gson.fromJson(body, HeartbeatPayload.class);
        } catch (Exception e) {
            respond(ex, 400, "{\"error\":\"invalid JSON\"}");
            return;
        }

        if (payload == null || payload.token() == null || payload.token().isBlank()) {
            respond(ex, 400, "{\"error\":\"missing token\"}");
            return;
        }

        TrustManager tm = plugin.getTrustManager();
        String nick = tm.getNickForToken(payload.token());
        if (nick == null) {
            respond(ex, 403, "{\"error\":\"unrecognized token — run /itadmin trust generate on the hub first\"}");
            return;
        }

        long now = System.currentTimeMillis();
        TrustManager.HeartbeatSnapshot snap = new TrustManager.HeartbeatSnapshot(
                nick,
                payload.players(),
                payload.tps(),
                payload.errors(),
                payload.version() != null ? payload.version() : "unknown",
                payload.uptimeSeconds(),
                now
        );

        // Schedule the state update onto the main server thread
        Bukkit.getScheduler().runTask(plugin, () -> tm.updateHeartbeat(nick, snap));

        respond(ex, 200, "{\"ok\":true,\"name\":\"" + nick + "\"}");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ── Payload record (Gson-mapped) ──────────────────────────────────────────

    /**
     * JSON payload sent by remote ITPlugin instances.
     * Fields match the names used in MonitorClient serialization.
     */
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
