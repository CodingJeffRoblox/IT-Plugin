package com.itplugin.monitor;

import com.itplugin.ITPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the list of servers that are trusted to send heartbeats to this
 * server's embedded monitor endpoint.
 *
 * Backed by plugins/ITPlugin/trusted-servers.yml (separate from config.yml).
 *
 * Thread-safe: all mutable state is in ConcurrentHashMap-backed maps.
 */
public class TrustManager {

    private final ITPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    // nick → token (authoritative, persisted)
    private final Map<String, String> nickToToken = new LinkedHashMap<>();

    // token → nick (reverse lookup, derived)
    private final Map<String, String> tokenToNick = new HashMap<>();

    // nick → latest heartbeat data (in-memory, not persisted)
    private final Map<String, HeartbeatSnapshot> heartbeats = new LinkedHashMap<>();

    public TrustManager(ITPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trusted-servers.yml");
        load();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    public void load() {
        if (!file.exists()) {
            yml = new YamlConfiguration();
            yml.set("_comment", "Managed by /itadmin trust. Do not edit manually.");
            return;
        }
        yml = YamlConfiguration.loadConfiguration(file);
        nickToToken.clear();
        tokenToNick.clear();

        var section = yml.getConfigurationSection("servers");
        if (section != null) {
            for (String nick : section.getKeys(false)) {
                String token = section.getString(nick + ".token");
                if (token != null && !token.isEmpty()) {
                    nickToToken.put(nick, token);
                    tokenToNick.put(token, nick);
                }
            }
        }
        plugin.getLogger().info("TrustManager: loaded " + nickToToken.size() + " trusted server(s).");
    }

    public void save() {
        yml.set("_comment", "Managed by /itadmin trust. Do not edit manually.");
        for (Map.Entry<String, String> e : nickToToken.entrySet()) {
            String path = "servers." + e.getKey();
            yml.set(path + ".token", e.getValue());
            // Preserve last-seen if already stored
            long ls = heartbeats.containsKey(e.getKey())
                    ? heartbeats.get(e.getKey()).lastSeen() : 0L;
            yml.set(path + ".last-seen-epoch", ls);
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save trusted-servers.yml: " + ex.getMessage());
        }
    }

    // ── Token management ─────────────────────────────────────────────────────

    /**
     * Generate a fresh UUID token for the given nick.
     * Overwrites any existing token for that nick.
     * Returns the new token string.
     */
    public String generateToken(String nick) {
        String token = UUID.randomUUID().toString();
        // Remove old reverse mapping
        String oldToken = nickToToken.get(nick);
        if (oldToken != null) tokenToNick.remove(oldToken);

        nickToToken.put(nick, token);
        tokenToNick.put(token, nick);
        save();
        return token;
    }

    /**
     * Returns the nick associated with the given token, or null if unrecognised.
     */
    public String getNickForToken(String token) {
        return tokenToNick.get(token);
    }

    public boolean isTokenValid(String token) {
        return tokenToNick.containsKey(token);
    }

    /**
     * Revoke the trust entry by nick. Returns false if the nick was not found.
     */
    public boolean revokeByNick(String nick) {
        String token = nickToToken.remove(nick);
        if (token == null) return false;
        tokenToNick.remove(token);
        heartbeats.remove(nick);
        // Remove from YAML too
        yml.set("servers." + nick, null);
        try { yml.save(file); } catch (IOException ex) { /* best-effort */ }
        return true;
    }

    public boolean hasNick(String nick) {
        return nickToToken.containsKey(nick);
    }

    public Map<String, String> getAllTrustedNicks() {
        return new LinkedHashMap<>(nickToToken);
    }

    // ── Heartbeat data ───────────────────────────────────────────────────────

    /**
     * Called by MonitorHttpServer when a valid heartbeat arrives.
     * Must be called from the main server thread (or be internally thread-safe).
     */
    public void updateHeartbeat(String nick, HeartbeatSnapshot snap) {
        heartbeats.put(nick, snap);
    }

    public Map<String, HeartbeatSnapshot> getAllHeartbeats() {
        return new LinkedHashMap<>(heartbeats);
    }

    public HeartbeatSnapshot getHeartbeat(String nick) {
        return heartbeats.get(nick);
    }

    /**
     * Register a server discovered via the cloud relay (no token required).
     * Safe to call repeatedly — does nothing if the nick is already registered.
     */
    public void registerCloud(String nick) {
        String key = nick.toLowerCase();
        if (!nickToToken.containsKey(key)) {
            // Use a "cloud:" prefix so we can distinguish from token-based entries
            nickToToken.put(key, "cloud:" + key);
        }
    }

    // ── HeartbeatSnapshot ────────────────────────────────────────────────────

    /**
     * Immutable snapshot of one server's last heartbeat.
     *
     * @param nick        server name as registered via /itadmin trust generate
     * @param players     online player count reported by that server
     * @param tps         TPS reported by that server
     * @param errors      number of captured console errors on that server
     * @param version     Minecraft version string (e.g. "1.21.4")
     * @param uptimeSeconds seconds since that server's ITPlugin enabled
     * @param lastSeen    System.currentTimeMillis() when this snapshot was received
     */
    public record HeartbeatSnapshot(
            String nick,
            int players,
            double tps,
            int errors,
            String version,
            long uptimeSeconds,
            long lastSeen
    ) {}
}
