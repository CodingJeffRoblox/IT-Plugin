package com.itplugin.command;

import com.itplugin.ITPlugin;
import com.itplugin.gui.MainMenuGUI;
import com.itplugin.monitor.MonitorClient;
import com.itplugin.monitor.MonitorHttpServer;
import com.itplugin.monitor.TrustManager;
import com.itplugin.util.ConfigValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * /itadmin [gui|monitor|validate|errors|clearerrors|reload|trust|dashboard]
 *
 * Trust subcommands (all require itplugin.admin):
 *   /itadmin trust generate <nick>         — hub: generate a token for a remote server
 *   /itadmin trust connect <url> <token> <nick> — remote: register with the hub
 *   /itadmin trust list                    — hub: list all trusted servers + heartbeat status
 *   /itadmin trust revoke <nick>           — hub: revoke a server's trust
 *   /itadmin trust status                  — hub: live status of all connected servers
 */
public class ITAdminCommand implements CommandExecutor, TabCompleter {

    private final ITPlugin plugin;

    public ITAdminCommand(ITPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();

        if (sender instanceof Player player && !lp.isAdmin(player)) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player) && !sender.isOp()) {
            sender.sendMessage(mm.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                new MainMenuGUI(plugin, player).open(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {

            case "gui", "menu" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(mm.get("player-only")); yield true; }
                new MainMenuGUI(plugin, player).open(player);
                yield true;
            }

            case "monitor", "status", "network" -> {
                if (sender instanceof org.bukkit.entity.Player p) {
                    // Open the live server-network GUI (auto-refresh task started by GUIListener)
                    plugin.getGUIListener().openServerMonitor(p);
                } else {
                    // Console: print text-based status
                    plugin.getServerMonitor().getStatusReport().forEach(sender::sendMessage);
                    showTrustStatus(sender);
                }
                yield true;
            }

            case "validate", "check" -> {
                sender.sendMessage(mm.get("validate-scanning"));
                List<String> dirs = plugin.getConfig().getStringList("config-validator.scan-directories");
                boolean rw = plugin.getConfig().getBoolean("config-validator.report-warnings", true);
                int total = 0;
                for (String d : dirs) {
                    for (var r : ConfigValidator.validatePluginConfigs(new File(d), rw)) {
                        for (String err : r.errors()) {
                            sender.sendMessage(mm.color("&c[ERR]  &f" + r.file().getName() + "&8: &7" + err));
                            total++;
                        }
                        for (String w : r.warnings()) {
                            sender.sendMessage(mm.color("&e[WARN] &f" + r.file().getName() + "&8: &7" + w));
                            total++;
                        }
                    }
                }
                sender.sendMessage(total == 0 ? mm.get("validate-clean") : mm.get("validate-issues", total));
                yield true;
            }

            case "errors", "log" -> {
                int page = (args.length >= 2) ? safeInt(args[1], 1) : 1;
                if (sender instanceof Player player) {
                    // Rich clickable display with server/source info and copy-to-clipboard
                    plugin.getGUIListener().showErrors(player, page);
                } else {
                    // Console: plain text with server + source columns
                    var entries = plugin.getConsoleMonitor().getCapturedEntries();
                    if (entries.isEmpty()) { sender.sendMessage(mm.get("admin-errors-none")); yield true; }
                    int pageSize   = 8;
                    int totalPages = (int) Math.ceil(entries.size() / (double) pageSize);
                    page = Math.max(1, Math.min(page, totalPages));
                    int start = (page - 1) * pageSize;
                    int end   = Math.min(start + pageSize, entries.size());
                    sender.sendMessage("=== Console Errors (page " + page + "/" + totalPages + ", " + entries.size() + " total) ===");
                    for (int i = start; i < end; i++) {
                        var e = entries.get(i);
                        String msg = e.message().length() > 80 ? e.message().substring(0, 80) + "…" : e.message();
                        sender.sendMessage("  [" + (i + 1) + "] [" + e.level() + "] [" + e.serverName() + "] [" + e.source() + "] " + msg);
                    }
                    if (page < totalPages) sender.sendMessage("  Next: /itadmin errors " + (page + 1));
                }
                yield true;
            }

            case "clearerrors", "clear" -> {
                plugin.getConsoleMonitor().clearErrors();
                sender.sendMessage(mm.get("admin-errors-cleared"));
                yield true;
            }

            case "reload" -> {
                plugin.reloadConfig();
                plugin.getMessageManager().load();
                // Re-apply monitor HTTP server state from (possibly updated) config
                if (plugin.getConfig().getBoolean("monitor.http.enabled", false)) {
                    boolean ok = plugin.startMonitorHttpServer();
                    if (ok) {
                        int port = plugin.getMonitorHttpServer().getPort();
                        sender.sendMessage(mm.color(mm.get("prefix") + "&aHTTP monitor server (re)started on port &f" + port + "&a."));
                    } else {
                        sender.sendMessage(mm.color(mm.get("prefix-error") + "&cFailed to start HTTP monitor server — check server logs."));
                    }
                }
                sender.sendMessage(mm.get("admin-config-reloaded"));
                yield true;
            }

            case "cloud" -> {
                handleCloud(sender, args);
                yield true;
            }

            case "trust" -> {
                handleTrust(sender, args);
                yield true;
            }

            default -> { sendHelp(sender); yield true; }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /itadmin trust
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleTrust(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();

        if (args.length < 2) { sendTrustHelp(sender); return; }

        switch (args[1].toLowerCase()) {

            case "generate" -> {
                // /itadmin trust generate <nick>
                if (args.length < 3) {
                    sender.sendMessage(mm.color("&cUsage: /itadmin trust generate <serverNick>"));
                    return;
                }
                String nick = args[2].toLowerCase();

                // Auto-enable the HTTP server if not already on
                ensureHttpServerRunning(sender);

                TrustManager tm = plugin.getTrustManager();
                boolean existing = tm.hasNick(nick);
                String token = tm.generateToken(nick);

                int port = plugin.getMonitorHttpServer() != null
                        ? plugin.getMonitorHttpServer().getPort()
                        : plugin.getConfig().getInt("monitor.http.port", 28080);

                sender.sendMessage(mm.color("&8&m                                        &r"));
                sender.sendMessage(mm.color(mm.get("prefix") + (existing ? "&eTrust token &f regenerated" : "&aTrust token &fgenerated") + " for server &b'" + nick + "'"));
                sender.sendMessage(mm.color("&7Run this command on the &b" + nick + " &7server:"));
                sender.sendMessage(mm.color("&f  /itadmin trust connect http://THIS-SERVER-IP:" + port + " " + token + " " + nick));
                sender.sendMessage(mm.color("&8Replace &7THIS-SERVER-IP &8with your hub's public/LAN IP address."));
                sender.sendMessage(mm.color("&8Token is valid until revoked with &7/itadmin trust revoke " + nick + "&8."));
                sender.sendMessage(mm.color("&8&m                                        &r"));
            }

            case "connect" -> {
                // /itadmin trust connect <url> <token> <nick>
                // url example: http://192.168.1.10:28080
                if (args.length < 5) {
                    sender.sendMessage(mm.color("&cUsage: /itadmin trust connect <hub-url> <token> <this-server-name>"));
                    sender.sendMessage(mm.color("&7Example: /itadmin trust connect http://192.168.1.10:28080 abc123 survival"));
                    return;
                }
                String hubUrl  = args[2];
                String token   = args[3];
                String selfNick = args[4].toLowerCase();

                sender.sendMessage(mm.color(mm.get("prefix") + "&7Connecting to hub at &f" + hubUrl + "&7..."));

                // Perform the HTTP ping + first heartbeat async so we don't block the main thread
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String result = verifyAndConnect(hubUrl, token, selfNick);
                    // Report result back on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
                });
            }

            case "list" -> {
                TrustManager tm = plugin.getTrustManager();
                Map<String, String> trusted = tm.getAllTrustedNicks();
                if (trusted.isEmpty()) {
                    sender.sendMessage(mm.color(mm.get("prefix") + "&7No trusted servers yet. Use &f/itadmin trust generate <nick>&7."));
                    return;
                }
                sender.sendMessage(mm.color("&8&m                                        &r"));
                sender.sendMessage(mm.color(mm.get("prefix") + "&bTrusted Servers (" + trusted.size() + ")"));
                long now = System.currentTimeMillis();
                for (String nick : trusted.keySet()) {
                    TrustManager.HeartbeatSnapshot snap = tm.getHeartbeat(nick);
                    if (snap == null) {
                        sender.sendMessage(mm.color("  &f" + nick + " &8— &enever connected"));
                    } else {
                        long ageSec = (now - snap.lastSeen()) / 1000;
                        String ageStr = ageSec < 60 ? ageSec + "s ago" : (ageSec / 60) + "m ago";
                        String tpsColor = snap.tps() >= 19.5 ? "&a" : snap.tps() >= 15 ? "&e" : "&c";
                        sender.sendMessage(mm.color(
                                "  &f" + nick
                                + " &8— &a" + snap.players() + " players"
                                + " &8| " + tpsColor + String.format("%.1f", snap.tps()) + " TPS"
                                + " &8| &7last seen " + ageStr));
                    }
                }
                sender.sendMessage(mm.color("&8&m                                        &r"));
            }

            case "revoke" -> {
                if (args.length < 3) {
                    sender.sendMessage(mm.color("&cUsage: /itadmin trust revoke <serverNick>"));
                    return;
                }
                String nick = args[2].toLowerCase();
                boolean ok = plugin.getTrustManager().revokeByNick(nick);
                if (ok) {
                    sender.sendMessage(mm.color(mm.get("prefix") + "&aRevoked trust for &f'" + nick + "'&a. Their next heartbeat will be rejected."));
                } else {
                    sender.sendMessage(mm.color(mm.get("prefix-error") + "&cNo trusted server named &f'" + nick + "'&c found."));
                }
            }

            case "status" -> showTrustStatus(sender);

            default -> sendTrustHelp(sender);
        }
    }

    // ── HTTP connect helper (runs async) ─────────────────────────────────────

    private String verifyAndConnect(String hubUrlRaw, String token, String selfNick) {
        var mm = plugin.getMessageManager();

        // ── Pre-flight: fix common mistakes before even connecting ────────────
        StringBuilder warnings = new StringBuilder();

        // 1. Reject https:// — the embedded monitor server is plain HTTP
        String hubUrl = hubUrlRaw;
        if (hubUrl.startsWith("https://")) {
            hubUrl = "http://" + hubUrl.substring(8);
            warnings.append(mm.color("&e⚠ Changed https:// → http:// (the IT monitor does not use TLS)\n"));
        }

        // 2. Warn about Minecraft-style ports being wrong
        try {
            java.net.URI parsedUri = java.net.URI.create(hubUrl);
            int port = parsedUri.getPort();
            if (port == 25565 || port == 25566 || port == 19132) {
                warnings.append(mm.color("&c✗ Port &f" + port
                        + " &cis the Minecraft server port, not the IT monitor port.\n"
                        + "&c  The IT monitor HTTP server runs on port &f28080 &cby default.\n"
                        + "&c  Correct URL example: &fhttp://" + parsedUri.getHost() + ":28080\n"));
                return warnings
                        + mm.color("&c  Run &f/itadmin trust generate <nick> &con the hub to see the exact URL.");
            }
        } catch (IllegalArgumentException ignored) {
            return mm.color(mm.get("prefix-error") + "&cInvalid URL: &f" + hubUrl
                    + "\n&7  Example: &fhttp://192.168.1.10:28080");
        }

        // 3. Warn if token looks like an IP or is obviously wrong
        if (token.contains(":") || token.contains(".")) {
            return mm.color(mm.get("prefix-error") + "&cThe second argument should be the &ftoken UUID&c, not an IP address.\n"
                    + "&7  Run &f/itadmin trust generate " + selfNick
                    + " &7on the &bhub server&7 first — it will print the exact command to run here.\n"
                    + "&7  Usage: &f/itadmin trust connect <hub-url> <token> <this-server-name>");
        }

        String baseUrl = hubUrl.replaceAll("/+$", "");
        String warningPrefix = warnings.length() > 0 ? warnings.toString() : "";

        // 4. Attempt connection
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            HttpRequest ping = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/itplugin/ping"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> pong = client.send(ping, HttpResponse.BodyHandlers.ofString());
            if (pong.statusCode() != 200 || !pong.body().contains("ITPlugin")) {
                return warningPrefix + mm.color(mm.get("prefix-error")
                        + "&cHub at &f" + baseUrl + " &cdid not respond as an ITPlugin hub (got HTTP "
                        + pong.statusCode() + ").\n"
                        + "&7  Check: (1) hub has &fmonitor.http.enabled: true&7 in config.yml\n"
                        + "&7         (2) port &f" + java.net.URI.create(baseUrl).getPort()
                        + "&7 is open in the firewall\n"
                        + "&7         (3) ITPlugin is installed on the hub server");
            }

            // 5. Send a test heartbeat to verify token
            String payload = "{\"token\":\"" + token + "\",\"name\":\"" + selfNick
                    + "\",\"players\":0,\"tps\":20.0,\"errors\":0,\"version\":\"probe\",\"uptimeSeconds\":0}";

            HttpRequest hb = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/itplugin/heartbeat"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> hbResp = client.send(hb, HttpResponse.BodyHandlers.ofString());
            if (hbResp.statusCode() == 403) {
                return warningPrefix + mm.color(mm.get("prefix-error")
                        + "&cToken rejected by hub.\n"
                        + "&7  Make sure you ran &f/itadmin trust generate " + selfNick
                        + " &7on the hub server first,\n"
                        + "&7  then copy the &fexact token&7 it printed.");
            }
            if (hbResp.statusCode() != 200) {
                return warningPrefix + mm.color(mm.get("prefix-error")
                        + "&cUnexpected response from hub: HTTP " + hbResp.statusCode());
            }

            // 6. Token verified — persist config and start client
            final String savedUrl = baseUrl;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getConfig().set("monitor.client.enabled", true);
                plugin.getConfig().set("monitor.client.main-server", savedUrl);
                plugin.getConfig().set("monitor.client.token", token);
                plugin.getConfig().set("monitor.client.server-name", selfNick);
                plugin.saveConfig();

                if (plugin.getMonitorClient() != null) plugin.getMonitorClient().stop();
                MonitorClient mc = new MonitorClient(plugin);
                mc.start();
            });

            return warningPrefix + mm.color("&8&m                                        &r\n"
                    + mm.get("prefix") + "&a✓ Connected to hub at &f" + baseUrl + "\n"
                    + mm.get("prefix") + "&7This server (&b" + selfNick + "&7) will now send heartbeats every 30s.\n"
                    + mm.get("prefix") + "&7Use &f/itadmin monitor &7on the hub to see live status.\n"
                    + "&8&m                                        &r");

        } catch (java.net.ConnectException e) {
            return warningPrefix + mm.color(mm.get("prefix-error")
                    + "&cCould not reach &f" + baseUrl + "\n"
                    + "&7  Check: (1) hub IP is correct\n"
                    + "&7         (2) port &f" + extractPort(baseUrl) + " &7is open in firewall / port-forwarded\n"
                    + "&7         (3) hub server is online with ITPlugin loaded");
        } catch (javax.net.ssl.SSLException e) {
            return warningPrefix + mm.color(mm.get("prefix-error")
                    + "&cSSL error connecting to &f" + baseUrl + "\n"
                    + "&7  The IT monitor does not support TLS — use &fhttp://&7 not &fhttps://");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return warningPrefix + mm.color(mm.get("prefix-error") + "&cConnection error: &f" + msg);
        }
    }

    private String extractPort(String url) {
        try { return String.valueOf(java.net.URI.create(url).getPort()); } catch (Exception e) { return "28080"; }
    }

    // ── Trust status display ─────────────────────────────────────────────────

    private void showTrustStatus(CommandSender sender) {
        var mm = plugin.getMessageManager();
        TrustManager tm = plugin.getTrustManager();
        Map<String, String> trusted = tm.getAllTrustedNicks();
        if (trusted.isEmpty()) return;   // nothing to show

        long now = System.currentTimeMillis();
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bTrust-Connected Servers (" + trusted.size() + " registered)"));
        for (String nick : trusted.keySet()) {
            TrustManager.HeartbeatSnapshot snap = tm.getHeartbeat(nick);
            if (snap == null) {
                sender.sendMessage(mm.color("  &8● &7" + nick + " &8— &enever sent a heartbeat"));
                continue;
            }
            long ageSec = (now - snap.lastSeen()) / 1000;
            boolean stale = ageSec > 90;
            String status = stale ? "&c✗ offline" : "&a✓ online";
            String ageStr = ageSec < 60 ? ageSec + "s ago" : (ageSec / 60) + "m " + (ageSec % 60) + "s ago";
            String tpsColor = snap.tps() >= 19.5 ? "&a" : snap.tps() >= 15 ? "&e" : "&c";
            sender.sendMessage(mm.color(
                    "  &f" + nick + " " + status
                    + " &8| &a" + snap.players() + " &7players"
                    + " &8| " + tpsColor + String.format("%.1f", snap.tps()) + " TPS"
                    + " &8| &7" + snap.errors() + " errors"
                    + " &8| &7seen " + ageStr));
        }
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    // ── Cloud relay setup ────────────────────────────────────────────────────

    private void handleCloud(CommandSender sender, String[] args) {
        var mm = plugin.getMessageManager();
        var lp = plugin.getLuckPermsManager();
        // Console always permitted; players need admin rank
        if (sender instanceof org.bukkit.entity.Player p && !lp.isAdmin(p)) {
            sender.sendMessage(mm.get("no-permission")); return;
        }

        // /itadmin cloud setup <api-url> <api-key> <nick>
        if (args.length < 2) {
            sender.sendMessage(mm.color(mm.get("prefix") + "&bIT Cloud Monitor"));
            boolean enabled = plugin.getConfig().getBoolean("monitor.cloud.enabled", false);
            sender.sendMessage(mm.color("  Status: " + (enabled ? "&aEnabled" : "&cDisabled")));
            if (enabled) {
                sender.sendMessage(mm.color("  Nick: &f" + plugin.getConfig().getString("monitor.cloud.server-nick", "?")));
                sender.sendMessage(mm.color("  API:  &f" + plugin.getConfig().getString("monitor.cloud.api-url", "?")));
            }
            sender.sendMessage(mm.color("  &7Usage: &f/itadmin cloud setup <api-url> <api-key> <nick>"));
            return;
        }

        if (!args[1].equalsIgnoreCase("setup")) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "Unknown sub-command. Try: &f/itadmin cloud setup <api-url> <api-key> <nick>"));
            return;
        }

        if (args.length < 5) {
            sender.sendMessage(mm.color(mm.get("prefix-error") + "Usage: &f/itadmin cloud setup <api-url> <api-key> <nick>"));
            sender.sendMessage(mm.color("  &7Example: &f/itadmin cloud setup https://abc123.replit.app/api myKey Hub"));
            return;
        }

        String apiUrl = args[2].replaceAll("/+$", "");
        String apiKey = args[3];
        String nick   = args[4];

        plugin.getConfig().set("monitor.cloud.enabled",     true);
        plugin.getConfig().set("monitor.cloud.api-url",     apiUrl);
        plugin.getConfig().set("monitor.cloud.api-key",     apiKey);
        plugin.getConfig().set("monitor.cloud.server-nick", nick);
        plugin.saveConfig();

        plugin.startCloudMonitor();

        sender.sendMessage(mm.color(mm.get("prefix") + "&aCloud monitor configured!"));
        sender.sendMessage(mm.color("  Nick:    &f" + nick));
        sender.sendMessage(mm.color("  API URL: &f" + apiUrl));
        sender.sendMessage(mm.color("  &7Heartbeats will begin in ~5 seconds."));
        sender.sendMessage(mm.color("  &7Run this command on ALL servers (hub + sub-servers) with their own nick."));
    }

    // ── HTTP server auto-start ───────────────────────────────────────────────

    private void ensureHttpServerRunning(CommandSender sender) {
        var mm = plugin.getMessageManager();
        if (plugin.getMonitorHttpServer() != null) return; // already running — nothing to do

        // Start it right now (no reload needed)
        sender.sendMessage(mm.color(mm.get("prefix") + "&7Starting IT Monitor HTTP server…"));
        boolean ok = plugin.startMonitorHttpServer();
        if (ok) {
            int port = plugin.getMonitorHttpServer().getPort();
            sender.sendMessage(mm.color(mm.get("prefix") + "&aHTTP monitor server started on port &f"
                    + port + "&a. Make sure this port is open in your firewall."));
        } else {
            sender.sendMessage(mm.color(mm.get("prefix-error")
                    + "&cFailed to start HTTP monitor server — see console for details."));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Help
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendHelp(CommandSender sender) {
        var mm = plugin.getMessageManager();
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bIT Admin Panel"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color("  &f/itadmin               &8— Open GUI control panel"));
        sender.sendMessage(mm.color("  &f/itadmin monitor       &8— Cross-server + trust status"));
        sender.sendMessage(mm.color("  &f/itadmin validate      &8— Scan plugin configs"));
        sender.sendMessage(mm.color("  &f/itadmin errors [pg]   &8— View console errors"));
        sender.sendMessage(mm.color("  &f/itadmin clearerrors   &8— Clear error log"));
        sender.sendMessage(mm.color("  &f/itadmin reload        &8— Reload config + messages"));
        sender.sendMessage(mm.color("  &f/itadmin trust         &8— Manage trusted server connections"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    private void sendTrustHelp(CommandSender sender) {
        var mm = plugin.getMessageManager();
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color(mm.get("prefix") + "&bTrust Management"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
        sender.sendMessage(mm.color("  &8Hub (monitoring server):"));
        sender.sendMessage(mm.color("  &f/itadmin trust generate <nick>    &8— Create a token for a remote server"));
        sender.sendMessage(mm.color("  &f/itadmin trust list               &8— List all trusted servers"));
        sender.sendMessage(mm.color("  &f/itadmin trust status             &8— Live heartbeat status"));
        sender.sendMessage(mm.color("  &f/itadmin trust revoke <nick>      &8— Revoke a server's access"));
        sender.sendMessage(mm.color("  &8Remote (monitored server):"));
        sender.sendMessage(mm.color("  &f/itadmin trust connect <url> <token> <nick>  &8— Connect to hub"));
        sender.sendMessage(mm.color("&8&m                                        &r"));
    }

    private int safeInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("gui", "monitor", "validate", "errors", "clearerrors", "reload", "trust")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            return List.of("generate", "connect", "list", "revoke", "status")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trust") && args[1].equalsIgnoreCase("revoke")) {
            return new java.util.ArrayList<>(plugin.getTrustManager().getAllTrustedNicks().keySet());
        }
        return List.of();
    }
}
