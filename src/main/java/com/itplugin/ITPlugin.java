package com.itplugin;

import com.itplugin.command.HelpDeskCommand;
import com.itplugin.command.ITAdminCommand;
import com.itplugin.command.ITBroadcastCommand;
import com.itplugin.command.ITConsoleCommand;
import com.itplugin.command.ITHelpCommand;
import com.itplugin.command.ITNotesCommand;
import com.itplugin.listener.GUIListener;
import com.itplugin.listener.PlayerListener;
import com.itplugin.manager.LuckPermsManager;
import com.itplugin.manager.MessageManager;
import com.itplugin.manager.NoteManager;
import com.itplugin.monitor.CloudMonitorClient;
import com.itplugin.monitor.CloudMonitorPoller;
import com.itplugin.monitor.ConsoleMonitor;
import com.itplugin.monitor.ConsoleReader;
import com.itplugin.monitor.MonitorClient;
import com.itplugin.monitor.MonitorHttpServer;
import com.itplugin.monitor.ServerMonitor;
import com.itplugin.monitor.TrustManager;
import com.itplugin.ticket.TicketManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ITPlugin extends JavaPlugin {

    private MessageManager    messageManager;
    private LuckPermsManager  luckPermsManager;
    private TicketManager     ticketManager;
    private NoteManager       noteManager;
    private ConsoleMonitor    consoleMonitor;
    private ConsoleReader     consoleReader;
    private ServerMonitor     serverMonitor;
    private TrustManager      trustManager;
    private MonitorHttpServer  monitorHttpServer;
    private MonitorClient      monitorClient;
    private CloudMonitorClient cloudMonitorClient;
    private CloudMonitorPoller cloudMonitorPoller;
    private GUIListener        guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messageManager   = new MessageManager(this);
        luckPermsManager = new LuckPermsManager(this);
        ticketManager    = new TicketManager(this);
        noteManager      = new NoteManager(this);
        trustManager     = new TrustManager(this);

        // Console error monitor (alerts staff on ERROR/SEVERE)
        consoleMonitor = new ConsoleMonitor(this);
        if (getConfig().getBoolean("console-monitor.enabled", true)) {
            consoleMonitor.register();
        }

        // Console reader (captures ALL output for the in-game terminal)
        consoleReader = new ConsoleReader(this);
        if (getConfig().getBoolean("console-terminal.enabled", true)) {
            consoleReader.register();
        }

        // BungeeCord cross-server monitor (optional, legacy)
        serverMonitor = new ServerMonitor(this);
        if (getConfig().getBoolean("server-monitor.enabled", true)) {
            serverMonitor.start();
        }

        // ── Trust-based HTTP monitor server (hub role) ─────────────────────
        if (getConfig().getBoolean("monitor.http.enabled", false)) {
            monitorHttpServer = new MonitorHttpServer(this);
            try {
                monitorHttpServer.start();
            } catch (Exception e) {
                getLogger().severe("Failed to start IT Monitor HTTP server: " + e.getMessage()
                        + " — run /itadmin trust generate to set it up.");
            }
        }

        // ── Trust-based HTTP monitor client (remote-server role) ───────────
        if (getConfig().getBoolean("monitor.client.enabled", false)) {
            monitorClient = new MonitorClient(this);
            monitorClient.start();
        }

        // ── Cloud relay monitor (all servers, works on any host) ───────────
        if (getConfig().getBoolean("monitor.cloud.enabled", false)) {
            cloudMonitorClient = new CloudMonitorClient(this);
            cloudMonitorClient.start();
            // Also start the poller (reads all servers from cloud — hub role)
            cloudMonitorPoller = new CloudMonitorPoller(this);
            cloudMonitorPoller.start();
        }

        // Listeners
        guiListener = new GUIListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Commands
        registerCommand("ticket",      new HelpDeskCommand(this));
        registerCommand("itadmin",     new ITAdminCommand(this));
        registerCommand("itconsole",   new ITConsoleCommand(this));
        registerCommand("itterm",      new ITConsoleCommand(this));
        registerCommand("itbroadcast", new ITBroadcastCommand(this));
        registerCommand("itnotes",     new ITNotesCommand(this));
        registerCommand("ithelp",      new ITHelpCommand(this));

        getLogger().info("ITPlugin enabled — Help Desk, Terminal, GUI, LuckPerms, Console Monitor, Trust Monitor, Notes ready.");
    }

    @Override
    public void onDisable() {
        if (consoleMonitor    != null) consoleMonitor.unregister();
        if (consoleReader     != null) consoleReader.unregister();
        if (serverMonitor     != null) serverMonitor.stop();
        if (monitorHttpServer  != null) monitorHttpServer.stop();
        if (monitorClient      != null) monitorClient.stop();
        if (cloudMonitorClient != null) cloudMonitorClient.stop();
        if (cloudMonitorPoller != null) cloudMonitorPoller.stop();
        if (ticketManager     != null) ticketManager.save();
        if (trustManager      != null) trustManager.save();
        getLogger().info("ITPlugin disabled.");
    }

    private void registerCommand(String name, Object executor) {
        var cmd = getCommand(name);
        if (cmd == null) return;
        if (executor instanceof org.bukkit.command.CommandExecutor ce) cmd.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc)   cmd.setTabCompleter(tc);
    }

    public MessageManager    getMessageManager()       { return messageManager; }
    public LuckPermsManager  getLuckPermsManager()     { return luckPermsManager; }
    public TicketManager     getTicketManager()        { return ticketManager; }
    public NoteManager       getNoteManager()          { return noteManager; }
    public ConsoleMonitor    getConsoleMonitor()       { return consoleMonitor; }
    public ConsoleReader     getConsoleReader()        { return consoleReader; }
    public ServerMonitor     getServerMonitor()        { return serverMonitor; }
    public TrustManager      getTrustManager()         { return trustManager; }
    public MonitorHttpServer  getMonitorHttpServer()    { return monitorHttpServer; }
    public MonitorClient      getMonitorClient()        { return monitorClient; }
    public CloudMonitorClient getCloudMonitorClient()   { return cloudMonitorClient; }
    public CloudMonitorPoller getCloudMonitorPoller()   { return cloudMonitorPoller; }
    public GUIListener        getGUIListener()          { return guiListener; }

    /** Start (or restart) the cloud monitor client and poller immediately. */
    public void startCloudMonitor() {
        if (cloudMonitorClient != null) { cloudMonitorClient.stop(); }
        if (cloudMonitorPoller != null) { cloudMonitorPoller.stop(); }
        cloudMonitorClient = new CloudMonitorClient(this);
        cloudMonitorClient.start();
        cloudMonitorPoller = new CloudMonitorPoller(this);
        cloudMonitorPoller.start();
    }

    /**
     * Starts (or restarts) the trust-based HTTP monitor server right now.
     * Safe to call at runtime — stops the old instance first if one is running.
     * Also saves {@code monitor.http.enabled=true} to config.
     *
     * @return true if the server started successfully
     */
    public boolean startMonitorHttpServer() {
        if (monitorHttpServer != null) {
            monitorHttpServer.stop();
            monitorHttpServer = null;
        }
        getConfig().set("monitor.http.enabled", true);
        saveConfig();
        monitorHttpServer = new MonitorHttpServer(this);
        try {
            monitorHttpServer.start();
            getLogger().info("IT Monitor HTTP server started on port "
                    + monitorHttpServer.getPort() + ".");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to start IT Monitor HTTP server: " + e.getMessage());
            monitorHttpServer = null;
            return false;
        }
    }
}
