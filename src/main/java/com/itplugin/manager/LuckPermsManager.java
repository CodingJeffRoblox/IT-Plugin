package com.itplugin.manager;

import com.itplugin.ITPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional LuckPerms integration — fully reflection-based so the plugin
 * compiles and runs without LuckPerms on the classpath.
 *
 * Permission checks always delegate to player.hasPermission() which LuckPerms
 * transparently hooks into for online players.  Reflection is used only for
 * richer metadata (prefix, primary group) and programmatic permission mutations.
 *
 * Install LuckPerms on the server to get prefix display and group queries.
 * Without it, all permission checks still work via Bukkit's permission system.
 */
public class LuckPermsManager {

    private final ITPlugin plugin;
    private Object luckPermsInstance; // net.luckperms.api.LuckPerms, held as Object
    private boolean available = false;

    public LuckPermsManager(ITPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    private void tryHook() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms not found — using Bukkit permissions (all checks still work).");
            return;
        }
        try {
            Class<?> apiClass = Class.forName("net.luckperms.api.LuckPerms");
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<?> rsp =
                    plugin.getServer().getServicesManager().getRegistration((Class) apiClass);
            if (rsp != null) {
                luckPermsInstance = rsp.getProvider();
                available = true;
                plugin.getLogger().info("LuckPerms integration enabled (reflection mode).");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("LuckPerms API class not found on classpath — falling back.");
        } catch (Exception e) {
            plugin.getLogger().warning("LuckPerms hook failed: " + e.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    // ─── Permission checks ───────────────────────────────────────────────────
    // LuckPerms integrates with Bukkit's permission system, so hasPermission()
    // is sufficient for all online player checks.

    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    public boolean isStaff(Player player) {
        return player.hasPermission("itplugin.staff") || player.hasPermission("itplugin.admin");
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("itplugin.admin");
    }

    // ─── Metadata (prefix / group) ───────────────────────────────────────────

    /**
     * Get the player's LuckPerms chat prefix (color-translated).
     * Returns empty string when LuckPerms is unavailable or no prefix is set.
     */
    public String getPrefix(Player player) {
        if (!available) return "";
        try {
            Object user = getUser(player.getUniqueId());
            if (user == null) return "";
            Object cachedData = invoke(user, "getCachedData");
            Object metaData = invoke(cachedData, "getMetaData");
            String prefix = (String) invoke(metaData, "getPrefix");
            return prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Get the player's primary LuckPerms group name.
     * Returns "default" when LuckPerms is unavailable.
     */
    public String getPrimaryGroup(Player player) {
        if (!available) return "default";
        try {
            Object user = getUser(player.getUniqueId());
            if (user == null) return "default";
            String group = (String) invoke(user, "getPrimaryGroup");
            return group != null ? group : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    // ─── Permission mutations ────────────────────────────────────────────────

    /**
     * Persistently add a permission node to a player via LuckPerms.
     * No-op when LuckPerms is unavailable.
     */
    public void addPermission(Player player, String permission) {
        if (!available) return;
        try {
            Object userManager = invoke(luckPermsInstance, "getUserManager");
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Class<?> nodeBuilderClass = Class.forName("net.luckperms.api.node.NodeBuilder");
            // Node.builder(permission).build()
            Method builderMethod = nodeClass.getDeclaredMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, permission);
            Object node = invoke(nodeBuilder, "build");

            // userManager.modifyUser(uuid, user -> user.data().add(node))
            final Object finalNode = node;
            java.util.function.Consumer<Object> mutator = u -> {
                try {
                    Object data = invoke(u, "data");
                    Method addMethod = data.getClass().getMethod("add", nodeClass);
                    addMethod.invoke(data, finalNode);
                } catch (Exception ignored) {}
            };
            Method modifyUser = userManager.getClass()
                    .getMethod("modifyUser", UUID.class, java.util.function.Consumer.class);
            modifyUser.invoke(userManager, player.getUniqueId(), mutator);
        } catch (Exception e) {
            plugin.getLogger().warning("LuckPerms addPermission failed: " + e.getMessage());
        }
    }

    /**
     * Persistently remove a permission node from a player via LuckPerms.
     * No-op when LuckPerms is unavailable.
     */
    public void removePermission(Player player, String permission) {
        if (!available) return;
        try {
            Object userManager = invoke(luckPermsInstance, "getUserManager");
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method builderMethod = nodeClass.getDeclaredMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, permission);
            Object node = invoke(nodeBuilder, "build");

            final Object finalNode = node;
            java.util.function.Consumer<Object> mutator = u -> {
                try {
                    Object data = invoke(u, "data");
                    Method removeMethod = data.getClass().getMethod("remove", nodeClass);
                    removeMethod.invoke(data, finalNode);
                } catch (Exception ignored) {}
            };
            Method modifyUser = userManager.getClass()
                    .getMethod("modifyUser", UUID.class, java.util.function.Consumer.class);
            modifyUser.invoke(userManager, player.getUniqueId(), mutator);
        } catch (Exception e) {
            plugin.getLogger().warning("LuckPerms removePermission failed: " + e.getMessage());
        }
    }

    // ─── Reflection helpers ──────────────────────────────────────────────────

    private Object getUser(UUID uuid) throws Exception {
        Object userManager = invoke(luckPermsInstance, "getUserManager");
        return invokeWith(userManager, "getUser", new Class[]{UUID.class}, new Object[]{uuid});
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method m = findMethod(target.getClass(), methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private Object invokeWith(Object target, String methodName, Class<?>[] types, Object[] args) throws Exception {
        Method m = target.getClass().getMethod(methodName, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private Method findMethod(Class<?> clazz, String name) throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
        }
        throw new NoSuchMethodException(name + " on " + clazz.getName());
    }
}
