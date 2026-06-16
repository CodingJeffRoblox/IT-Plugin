package com.itplugin.ticket;

import com.itplugin.ITPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TicketManager {

    private final ITPlugin plugin;
    private final File ticketFile;
    private final Map<Integer, Ticket> tickets = new LinkedHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public TicketManager(ITPlugin plugin) {
        this.plugin = plugin;
        this.ticketFile = new File(plugin.getDataFolder(), "tickets.yml");
        load();
    }

    public Ticket createTicket(String submitterName, UUID submitterUuid, String message) {
        int id = nextId.getAndIncrement();
        Ticket ticket = new Ticket(id, submitterName, submitterUuid, message);
        tickets.put(id, ticket);
        save();
        return ticket;
    }

    public Optional<Ticket> getTicket(int id) {
        return Optional.ofNullable(tickets.get(id));
    }

    public List<Ticket> getAllTickets() {
        return new ArrayList<>(tickets.values());
    }

    public List<Ticket> getTicketsFor(UUID uuid) {
        return tickets.values().stream()
                .filter(t -> t.getSubmitterUuid().equals(uuid))
                .collect(Collectors.toList());
    }

    public List<Ticket> getOpenTickets() {
        return tickets.values().stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED)
                .collect(Collectors.toList());
    }

    public int countOpenTicketsFor(UUID uuid) {
        return (int) tickets.values().stream()
                .filter(t -> t.getSubmitterUuid().equals(uuid) && t.getStatus() != TicketStatus.CLOSED)
                .count();
    }

    public void closeTicket(int id) {
        Ticket t = tickets.get(id);
        if (t != null) {
            t.setStatus(TicketStatus.CLOSED);
            save();
        }
    }

    public void assignTicket(int id, String staffName) {
        Ticket t = tickets.get(id);
        if (t != null) {
            t.setAssignee(staffName);
            t.setStatus(TicketStatus.IN_PROGRESS);
            save();
        }
    }

    public void addComment(int id, String commenterName, String comment) {
        Ticket t = tickets.get(id);
        if (t != null) {
            t.addComment("[" + commenterName + "] " + comment);
            save();
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId.get());
        for (Ticket ticket : tickets.values()) {
            String path = "tickets." + ticket.getId();
            config.set(path + ".submitter-name", ticket.getSubmitterName());
            config.set(path + ".submitter-uuid", ticket.getSubmitterUuid().toString());
            config.set(path + ".message", ticket.getMessage());
            config.set(path + ".created-at", ticket.getCreatedAt());
            config.set(path + ".status", ticket.getStatus().name());
            config.set(path + ".assignee", ticket.getAssignee());
            config.set(path + ".comments", ticket.getComments());
        }
        try {
            config.save(ticketFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tickets: " + e.getMessage());
        }
    }

    private void load() {
        if (!ticketFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(ticketFile);
        nextId.set(config.getInt("next-id", 1));
        ConfigurationSection section = config.getConfigurationSection("tickets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String name = section.getString(key + ".submitter-name", "Unknown");
                UUID uuid = UUID.fromString(Objects.requireNonNull(section.getString(key + ".submitter-uuid")));
                String message = section.getString(key + ".message", "");
                long createdAt = section.getLong(key + ".created-at");
                TicketStatus status = TicketStatus.valueOf(section.getString(key + ".status", "OPEN"));
                String assignee = section.getString(key + ".assignee");
                List<String> comments = section.getStringList(key + ".comments");
                tickets.put(id, new Ticket(id, name, uuid, message, createdAt, status, assignee, comments));
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load ticket #" + key + ": " + e.getMessage());
            }
        }
    }
}
