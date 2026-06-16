package com.itplugin.monitor;

import com.itplugin.ITPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Captures ALL server log output (INFO and above) into a circular ring buffer
 * for display in the in-game IT Terminal.
 *
 * Unlike ConsoleMonitor (which only captures errors to alert staff), this reader
 * captures everything a developer needs to see while working on the server.
 */
public class ConsoleReader extends Handler {

    public enum LogLevel { ALL, INFO, WARNING, SEVERE }

    public record LogLine(long timestamp, Level level, String loggerName, String message) {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

        public String formattedTime() {
            return FMT.format(
                    LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(timestamp),
                            java.time.ZoneId.systemDefault()));
        }

        /** Chat color-coded short representation for GUI items. */
        public String colorCode() {
            if (level.intValue() >= Level.SEVERE.intValue())   return "§c";
            if (level.intValue() >= Level.WARNING.intValue())  return "§e";
            return "§7";
        }

        /** One-line summary suitable for an item display name (truncated). */
        public String summary(int maxLen) {
            String raw = colorCode() + "[" + formattedTime() + "] " + message;
            return raw.length() <= maxLen ? raw : raw.substring(0, maxLen) + "§8…";
        }

        /** Full line for lore / book pages. */
        public String full() {
            return colorCode() + "[" + formattedTime() + "] [" + level.getName() + "] "
                    + "[" + shortLogger() + "] " + message;
        }

        private String shortLogger() {
            if (loggerName == null) return "?";
            int dot = loggerName.lastIndexOf('.');
            return dot >= 0 ? loggerName.substring(dot + 1) : loggerName;
        }
    }

    private final ITPlugin plugin;
    private final List<LogLine> lines = new ArrayList<>();
    private int maxLines;

    public ConsoleReader(ITPlugin plugin) {
        this.plugin = plugin;
        this.maxLines = plugin.getConfig().getInt("console-terminal.max-lines", 500);
    }

    public void register() {
        java.util.logging.Logger.getLogger("").addHandler(this);
        plugin.getLogger().info("IT Console Reader active (capturing up to " + maxLines + " lines).");
    }

    public void unregister() {
        java.util.logging.Logger.getLogger("").removeHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getMessage() == null) return;
        if (record.getLevel().intValue() < Level.INFO.intValue()) return;

        String msg = record.getMessage();
        if (record.getThrown() != null) {
            msg += " — " + record.getThrown().getClass().getSimpleName()
                    + ": " + record.getThrown().getMessage();
        }

        LogLine line = new LogLine(
                record.getMillis(),
                record.getLevel(),
                record.getLoggerName(),
                msg);

        synchronized (lines) {
            lines.add(line);
            if (lines.size() > maxLines) lines.remove(0);
        }
    }

    /**
     * Get all captured lines, optionally filtered by minimum log level.
     */
    public List<LogLine> getLines(LogLevel filter) {
        synchronized (lines) {
            if (filter == LogLevel.ALL) return new ArrayList<>(lines);
            int minValue = switch (filter) {
                case INFO    -> Level.INFO.intValue();
                case WARNING -> Level.WARNING.intValue();
                case SEVERE  -> Level.SEVERE.intValue();
                default      -> 0;
            };
            List<LogLine> out = new ArrayList<>();
            for (LogLine l : lines) {
                if (l.level().intValue() >= minValue) out.add(l);
            }
            return out;
        }
    }

    public void clear() {
        synchronized (lines) { lines.clear(); }
    }

    public int size() {
        synchronized (lines) { return lines.size(); }
    }

    @Override public void flush() {}
    @Override public void close() throws SecurityException {}
}
