package com.itplugin.manager;

import com.itplugin.ITPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages IT knowledge-base notes, persisted to plugins/ITPlugin/notes.yml.
 */
public class NoteManager {

    public record Note(int id, String title, String body, String author, long createdAt) {}

    private final ITPlugin plugin;
    private final File file;
    private final List<Note> notes = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public NoteManager(ITPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "notes.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int max = 0;
        for (String key : cfg.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String title  = cfg.getString(key + ".title", "Untitled");
                String body   = cfg.getString(key + ".body", "");
                String author = cfg.getString(key + ".author", "unknown");
                long ts       = cfg.getLong(key + ".createdAt", Instant.now().getEpochSecond());
                notes.add(new Note(id, title, body, author, ts));
                if (id > max) max = id;
            } catch (NumberFormatException ignored) {}
        }
        nextId.set(max + 1);
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Note n : notes) {
            cfg.set(n.id() + ".title",     n.title());
            cfg.set(n.id() + ".body",      n.body());
            cfg.set(n.id() + ".author",    n.author());
            cfg.set(n.id() + ".createdAt", n.createdAt());
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save notes.yml: " + e.getMessage());
        }
    }

    public Note addNote(String title, String body, String author) {
        Note n = new Note(nextId.getAndIncrement(), title, body, author, Instant.now().getEpochSecond());
        notes.add(n);
        save();
        return n;
    }

    public boolean deleteNote(int id) {
        boolean removed = notes.removeIf(n -> n.id() == id);
        if (removed) save();
        return removed;
    }

    public Note getNote(int id) {
        return notes.stream().filter(n -> n.id() == id).findFirst().orElse(null);
    }

    public List<Note> getAllNotes() {
        return List.copyOf(notes);
    }

    public List<Note> searchNotes(String query) {
        String q = query.toLowerCase();
        return notes.stream()
                .filter(n -> n.title().toLowerCase().contains(q) || n.body().toLowerCase().contains(q))
                .toList();
    }
}
