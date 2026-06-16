package com.itplugin.util;

import org.bukkit.ChatColor;

public final class ChatUtil {

    public static final String PREFIX = "§8[§bIT§8]§r ";
    public static final String PREFIX_WARN = "§8[§eIT§8]§r ";
    public static final String PREFIX_ERROR = "§8[§cIT§8]§r ";
    public static final String LINE = "§8§m                                        §r";

    private ChatUtil() {}

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String info(String message) {
        return PREFIX + message;
    }

    public static String warn(String message) {
        return PREFIX_WARN + "§e" + message;
    }

    public static String error(String message) {
        return PREFIX_ERROR + "§c" + message;
    }

    public static String success(String message) {
        return PREFIX + "§a" + message;
    }
}
