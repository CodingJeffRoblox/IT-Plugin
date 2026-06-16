package com.itplugin.monitor;

import com.itplugin.ITPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

public class ConsoleMonitor extends Handler {

    /** Structured error entry — includes server name and plugin/logger source. */
    public record ErrorEntry(String timestamp, String level, String serverName,
                             String source, String message, String fullText) {}

    private final ITPlugin plugin;
    private final List<Pattern> alertPatterns = new ArrayList<>();
    private final List<Pattern> ignorePatterns = new ArrayList<>();
    private final List<String> capturedErrors = new ArrayList<>();
    private final List<ErrorEntry> capturedEntries = new ArrayList<>();
    private final boolean alertStaff;
    private final boolean logToFile;
    private final File logFile;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_CAPTURED = 200;

    public ConsoleMonitor(ITPlugin plugin) {
        this.plugin = plugin;
        this.alertStaff = plugin.getConfig().getBoolean("console-monitor.alert-staff", true);
        this.logToFile  = plugin.getConfig().getBoolean("console-monitor.log-to-file", true);
        String logPath  = plugin.getConfig().getString("console-monitor.log-file", "plugins/ITPlugin/errors.log");
        this.logFile    = new File(logPath);

        for (String p : plugin.getConfig().getStringList("console-monitor.alert-patterns")) {
            try { alertPatterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE)); }
            catch (Exception e) { plugin.getLogger().warning("Invalid alert pattern: " + p); }
        }
        for (String p : plugin.getConfig().getStringList("console-monitor.ignore-patterns")) {
            try { ignorePatterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE)); }
            catch (Exception e) { plugin.getLogger().warning("Invalid ignore pattern: " + p); }
        }

        if (logFile.getParentFile() != null) logFile.getParentFile().mkdirs();
    }

    public void register() {
        java.util.logging.Logger.getLogger("").addHandler(this);
        plugin.getLogger().info("Console error monitor active.");
    }

    public void unregister() {
        java.util.logging.Logger.getLogger("").removeHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getMessage() == null) return;
        if (record.getLevel().intValue() < Level.WARNING.intValue()) return;

        String message = record.getMessage();

        for (Pattern ignore : ignorePatterns) {
            if (ignore.matcher(message).find()) return;
        }

        boolean matches = record.getLevel().intValue() >= Level.SEVERE.intValue();
        if (!matches) {
            for (Pattern alert : alertPatterns) {
                if (alert.matcher(message).find()) { matches = true; break; }
            }
        }
        if (!matches && record.getThrown() != null) matches = true;
        if (!matches) return;

        String timestamp  = LocalDateTime.now().format(FORMATTER);
        String level      = record.getLevel().getName();
        String serverName = plugin.getConfig().getString("server.name",
                Bukkit.getServer().getName().isEmpty() ? "Minecraft" : Bukkit.getServer().getName());
        String loggerName = record.getLoggerName() != null ? record.getLoggerName() : "unknown";
        String source     = shortSource(loggerName);

        String msg = message;
        if (record.getThrown() != null) {
            msg += " — " + record.getThrown().getClass().getSimpleName()
                 + ": " + record.getThrown().getMessage();
        }

        String fullEntry = "[" + timestamp + "] [" + level + "] [" + serverName + "] [" + source + "] " + msg;
        ErrorEntry entry = new ErrorEntry(timestamp, level, serverName, source, msg, fullEntry);

        synchronized (capturedErrors) {
            if (capturedErrors.size() >= MAX_CAPTURED) {
                capturedErrors.remove(0);
                capturedEntries.remove(0);
            }
            capturedErrors.add(fullEntry);
            capturedEntries.add(entry);
        }

        if (logToFile) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                pw.println(fullEntry);
            } catch (IOException ignored) {}
        }

        if (alertStaff) {
            String truncated = message.length() > 80 ? message.substring(0, 80) + "…" : message;
            final String alert = plugin.getMessageManager().get("console-error-alert", truncated);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (plugin.getLuckPermsManager().isStaff(p)) {
                        p.sendMessage(alert);
                    }
                }
            });
        }
    }

    /** Short readable name from a fully-qualified logger name. */
    private String shortSource(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) return "unknown";
        if (!loggerName.contains(".")) return loggerName;
        String[] parts = loggerName.split("\\.");
        return parts[parts.length - 1];
    }

    public List<String> getCapturedErrors() {
        synchronized (capturedErrors) { return new ArrayList<>(capturedErrors); }
    }

    public List<ErrorEntry> getCapturedEntries() {
        synchronized (capturedErrors) { return new ArrayList<>(capturedEntries); }
    }

    public void clearErrors() {
        synchronized (capturedErrors) {
            capturedErrors.clear();
            capturedEntries.clear();
        }
    }

    @Override public void flush() {}
    @Override public void close() throws SecurityException {}
}
