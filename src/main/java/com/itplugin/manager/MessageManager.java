package com.itplugin.manager;

import com.itplugin.ITPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class MessageManager {

    private final ITPlugin plugin;
    private YamlConfiguration messages;

    public MessageManager(ITPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge any missing keys from the bundled default
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    /**
     * Get a message by key, applying color codes and expanding prefix placeholders.
     * Positional args replace {0}, {1}, {2}, ... in the returned string.
     */
    public String get(String key, Object... args) {
        String raw = messages.getString(key, "&c[Missing message: " + key + "]");

        // Expand built-in prefix placeholders
        raw = raw.replace("{prefix}", raw(messages.getString("prefix", "&8[&bIT&8]&r ")));
        raw = raw.replace("{prefix-warn}", raw(messages.getString("prefix-warn", "&8[&eIT&8]&r ")));
        raw = raw.replace("{prefix-error}", raw(messages.getString("prefix-error", "&8[&cIT&8]&r ")));

        // Replace positional args
        for (int i = 0; i < args.length; i++) {
            raw = raw.replace("{" + i + "}", args[i] != null ? args[i].toString() : "");
        }

        return color(raw);
    }

    /** Translate color codes only (no prefix expansion, no args). */
    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Translate color codes without expanding prefix tokens. */
    private String raw(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Strip all color codes from a string. */
    public String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    /** Convenience: get the plain prefix (colored). */
    public String prefix() { return get("prefix"); }
}
