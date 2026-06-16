# ITPlugin — Developer Overview

> **Version:** 1.0.0 · **Target:** Spigot 1.21.4 · **Java:** 17  
> **Author:** CodingJeff · **Package:** `com.itplugin` · **Main class:** `com.itplugin.ITPlugin`

---

## Table of Contents

1. [Project Summary](#1-project-summary)
2. [File Structure](#2-file-structure)
3. [System Architecture](#3-system-architecture)
4. [Commands](#4-commands)
5. [Permissions](#5-permissions)
6. [Feature Modules](#6-feature-modules)
   - [Help Desk Ticketing](#61-help-desk-ticketing)
   - [GUI System](#62-gui-system)
   - [Console Error Monitor](#63-console-error-monitor)
   - [In-Game Console Terminal](#64-in-game-console-terminal)
   - [Cross-Server Monitor](#65-cross-server-monitor)
   - [Trust-Based HTTP Monitor](#66-trust-based-http-monitor)
   - [Cloud Relay Monitor](#67-cloud-relay-monitor)
   - [Knowledge Base (Notes)](#68-knowledge-base-notes)
   - [Config Validator](#69-config-validator)
   - [LuckPerms Integration](#610-luckperms-integration)
7. [Data Flow](#7-data-flow)
8. [Configuration Reference](#8-configuration-reference)
9. [Startup & Shutdown Sequence](#9-startup--shutdown-sequence)
10. [Build & Deploy](#10-build--deploy)

---

## 1. Project Summary

ITPlugin is a comprehensive **IT department toolkit** for Minecraft Spigot servers. It provides:

| Category | Features |
|---|---|
| 🎫 **Help Desk** | Full ticket lifecycle — create, view, assign, comment, close |
| 🖥️ **Terminal** | In-game live log viewer + console command executor |
| 📡 **Monitoring** | BungeeCord, direct HTTP trust, and cloud relay cross-server monitor |
| 🚨 **Alerts** | Real-time ERROR/SEVERE detection with in-game staff alerts |
| 📋 **Knowledge Base** | Searchable IT articles with in-game book editor |
| ✅ **Validator** | YAML config health check across all plugins |
| 🔐 **Permissions** | LuckPerms (reflection) + native Bukkit permission fallback |
| 🗂️ **GUIs** | 54-slot chest GUIs for all major features |

---

## 2. File Structure

```
ITPlugin/
├── pom.xml                                         # Maven build (Java 17, Spigot 1.21.4)
├── libs/
│   └── luckperms-api-5.4.jar                       # Compile-time ref (provided scope)
├── OVERVIEW.md                                     # This file
└── src/main/
    ├── java/com/itplugin/
    │   ├── ITPlugin.java                           # Main plugin class (bootstrap)
    │   │
    │   ├── command/
    │   │   ├── HelpDeskCommand.java                # /ticket
    │   │   ├── ITAdminCommand.java                 # /itadmin
    │   │   ├── ITBroadcastCommand.java             # /itbroadcast
    │   │   ├── ITConsoleCommand.java               # /itconsole, /itterm
    │   │   ├── ITHelpCommand.java                  # /ithelp
    │   │   └── ITNotesCommand.java                 # /itnotes
    │   │
    │   ├── gui/
    │   │   ├── ItemBuilder.java                    # Fluent ItemStack builder
    │   │   ├── MainMenuGUI.java                    # IT Control Panel (hub)
    │   │   ├── TicketListGUI.java                  # Paginated ticket list
    │   │   ├── TicketDetailGUI.java                # Single ticket view + actions
    │   │   ├── ConsoleTerminalGUI.java             # Live log viewer GUI
    │   │   ├── ServerMonitorGUI.java               # Cross-server status GUI
    │   │   ├── StaffDashboardGUI.java              # Staff overview panel
    │   │   └── KnowledgeBaseGUI.java               # IT articles browser
    │   │
    │   ├── listener/
    │   │   ├── GUIListener.java                    # All GUI clicks + chat inputs
    │   │   └── PlayerListener.java                 # Join notifications
    │   │
    │   ├── manager/
    │   │   ├── LuckPermsManager.java               # Reflection-based LP integration
    │   │   ├── MessageManager.java                 # messages.yml loader + formatter
    │   │   └── NoteManager.java                    # Knowledge base CRUD + persistence
    │   │
    │   ├── monitor/
    │   │   ├── ConsoleMonitor.java                 # JUL Handler → ERROR/SEVERE alerts
    │   │   ├── ConsoleReader.java                  # Full log capture (ring buffer)
    │   │   ├── ServerMonitor.java                  # BungeeCord plugin messaging
    │   │   ├── MonitorHttpServer.java              # Embedded HTTP server (hub role)
    │   │   ├── MonitorClient.java                  # HTTP heartbeat sender (remote role)
    │   │   ├── TrustManager.java                   # Token store + heartbeat snapshots
    │   │   ├── CloudMonitorClient.java             # Cloud relay heartbeat sender
    │   │   └── CloudMonitorPoller.java             # Cloud relay data poller (hub)
    │   │
    │   ├── ticket/
    │   │   ├── Ticket.java                         # Ticket model
    │   │   ├── TicketManager.java                  # Ticket CRUD + YAML persistence
    │   │   └── TicketStatus.java                   # OPEN / IN_PROGRESS / CLOSED
    │   │
    │   └── util/
    │       ├── ChatUtil.java                       # Shared color constants
    │       └── ConfigValidator.java                # Plugin YAML scanner
    │
    └── resources/
        ├── plugin.yml                              # Commands, permissions, metadata
        ├── config.yml                              # All feature configuration
        └── messages.yml                            # All user-facing strings
```

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         ITPlugin.java                           │
│  (bootstrap: wires managers, monitors, listeners, commands)     │
└────────────┬───────────────────────┬───────────────────────────┘
             │                       │
    ┌────────▼────────┐    ┌─────────▼──────────┐
    │    Managers     │    │     Monitors        │
    │  MessageManager │    │  ConsoleMonitor     │  ← JUL Handler
    │  LuckPermsManager│   │  ConsoleReader      │  ← JUL Handler (full)
    │  NoteManager    │    │  ServerMonitor      │  ← BungeeCord messaging
    │  TicketManager  │    │  MonitorHttpServer  │  ← Embedded HTTP server
    │  TrustManager   │    │  MonitorClient      │  ← HTTP heartbeat client
    └────────┬────────┘    │  CloudMonitorClient │  ← Cloud relay push
             │             │  CloudMonitorPoller │  ← Cloud relay pull
             │             └────────────────────┘
    ┌────────▼────────────────────────────────────┐
    │                  Listeners                  │
    │  GUIListener    ← inventory clicks + chat   │
    │  PlayerListener ← join alerts               │
    └────────┬────────────────────────────────────┘
             │
    ┌────────▼────────────────────────────────────┐
    │                  GUI Layer                  │
    │  MainMenuGUI → TicketListGUI → TicketDetail │
    │  ConsoleTerminalGUI  ServerMonitorGUI       │
    │  StaffDashboardGUI   KnowledgeBaseGUI       │
    └─────────────────────────────────────────────┘
```

**Key design principles:**
- **No compile-time LuckPerms dependency** — all LP API calls via reflection
- **Single GUIListener** routes all `InventoryClickEvent` to per-GUI handlers
- **Chat input state machine** — `awaiting` map tracks pending chat prompts per player
- **Ring-buffer log capture** — `ConsoleReader` keeps last N lines in memory (configurable)
- **Pluggable monitoring** — BungeeCord / HTTP trust / Cloud relay are independent and can all be disabled

---

## 4. Commands

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/ticket` | `/helpdesk` `/hd` `/itticket` | `itplugin.ticket.create` (open) | IT Help Desk — create, view, assign, close, comment |
| `/itadmin` | — | `itplugin.admin` | Admin panel — monitor, validate, errors, reload, trust, cloud |
| `/itconsole` | `/itterm` | `itplugin.console` | In-game terminal — live log viewer + command runner |
| `/itbroadcast` | `/itbc` | `itplugin.admin` | Send IT staff announcement to all players |
| `/itnotes` | `/itkb` `/itarticles` | `itplugin.staff` | Knowledge base — list, view, add, delete, search articles |
| `/ithelp` | `/itcommands` `/ith` | _(none required)_ | Show all ITPlugin commands, permission-filtered |

### Sub-command Reference

#### `/ticket`
```
/ticket                          → Open GUI (players) or text help (console)
/ticket create [message]         → Create ticket (chat prompt if no message)
/ticket list [all]               → List tickets (staff: all open; all = incl. closed)
/ticket view <id>                → View ticket details
/ticket close <id>               → Close a ticket
/ticket assign <id> <staff>      → Assign ticket to staff member
/ticket comment <id> <message>   → Add a comment to a ticket
```

#### `/itadmin`
```
/itadmin                         → Open GUI control panel
/itadmin monitor                 → Cross-server + trust heartbeat status
/itadmin validate                → Scan all plugin YAML configs
/itadmin errors [page]           → View captured console errors (click-to-copy)
/itadmin clearerrors             → Clear captured error log
/itadmin reload                  → Reload config.yml + messages.yml
/itadmin trust generate <nick>   → (Hub) Generate a token for a remote server
/itadmin trust connect <url> <token> <nick>  → (Remote) Connect to hub
/itadmin trust list              → (Hub) List all trusted servers
/itadmin trust revoke <nick>     → (Hub) Revoke a server's token
/itadmin trust status            → (Hub) Live heartbeat status of all servers
/itadmin cloud setup <url> <key> <nick> → Configure cloud relay monitor
```

#### `/itconsole`
```
/itconsole                       → Open terminal GUI
/itconsole exec <command>        → Run command as server console
/itconsole book [level]          → Export log to written book
/itconsole filter <level>        → Open terminal filtered to level (all/info/warn/severe)
/itconsole clear                 → Clear captured log buffer
```

#### `/itnotes`
```
/itnotes                         → Open GUI (players) or list (console)
/itnotes list [page]             → List all articles
/itnotes view <id>               → Read article body
/itnotes add [title|body]        → Add article (opens book editor in-game)
/itnotes delete <id>             → Delete article (admin only)
/itnotes search <query>          → Search title + body
```

---

## 5. Permissions

```
itplugin.admin          → Full access to all commands and GUIs (default: op)
  └─ itplugin.staff     → View/assign/close all tickets, receive alerts, manage notes
       ├─ itplugin.ticket.view.all
       ├─ itplugin.ticket.close.all
       └─ itplugin.ticket.assign
  └─ itplugin.console   → Access to IT Console Terminal (default: op)

itplugin.ticket.create      → Open new tickets (default: true — all players)
itplugin.ticket.view.own    → View own tickets (default: true)
itplugin.ticket.view.all    → View all tickets (default: false)
itplugin.ticket.close.own   → Close own tickets (default: true)
itplugin.ticket.close.all   → Close any ticket (default: false)
itplugin.ticket.assign      → Assign tickets to staff (default: false)
```

---

## 6. Feature Modules

### 6.1 Help Desk Ticketing

**Files:** `ticket/Ticket.java`, `ticket/TicketManager.java`, `ticket/TicketStatus.java`, `command/HelpDeskCommand.java`

**Storage:** `plugins/ITPlugin/tickets.yml` (YAML, persisted on every change)

**Ticket lifecycle:**
```
OPEN → (assign) → IN_PROGRESS → (close) → CLOSED
```

**Data model:**

| Field | Type | Description |
|---|---|---|
| `id` | `int` | Auto-incremented ticket ID |
| `submitterName` | `String` | Player name at creation time |
| `submitterUuid` | `UUID` | Player UUID |
| `message` | `String` | Issue description |
| `createdAt` | `long` | Unix epoch seconds |
| `status` | `TicketStatus` | OPEN / IN_PROGRESS / CLOSED |
| `assignee` | `String` (nullable) | Assigned staff name |
| `comments` | `List<String>` | `[name] comment` entries |

**Config options:**
- `helpdesk.max-open-tickets` — max simultaneous open tickets per player (default: 3)
- `helpdesk.notify-staff-on-create` — alert online staff on new ticket (default: true)
- `helpdesk.auto-close-days` — auto-close after N days of inactivity (0 = off)
- `helpdesk.id-prefix` — prefix for ticket IDs, e.g. `TICK` → `TICK-42`

---

### 6.2 GUI System

**Files:** `gui/*.java`, `listener/GUIListener.java`

All GUIs use 54-slot chest inventories. The `ItemBuilder` class provides a fluent builder for `ItemStack` construction with name, lore, and glow effects.

| GUI | Class | Purpose |
|---|---|---|
| Main Menu | `MainMenuGUI` | Central hub — access to all features |
| Ticket List | `TicketListGUI` | Paginated ticket browser |
| Ticket Detail | `TicketDetailGUI` | Single ticket — close/assign/comment actions |
| Console Terminal | `ConsoleTerminalGUI` | Live log viewer with filter and run-command |
| Server Monitor | `ServerMonitorGUI` | Cross-server status with auto-refresh |
| Staff Dashboard | `StaffDashboardGUI` | Summary: open tickets, errors, notes |
| Knowledge Base | `KnowledgeBaseGUI` | IT article browser + book editor launch |

**GUIListener routing:**
- `InventoryClickEvent` → checks `event.getInventory().getHolder()` type → dispatches to per-GUI handler method
- Chat input state machine: `awaiting` `Map<UUID, PendingInput>` tracks players waiting for chat input (ticket create/assign/comment, terminal command)
- `PlayerEditBookEvent` → captures signed written book → saves as knowledge base note

---

### 6.3 Console Error Monitor

**Files:** `monitor/ConsoleMonitor.java`

Extends `java.util.logging.Handler` and attaches to the root Java logger. Captures `WARNING` and above log records that match alert patterns.

**How it works:**
1. Registered on `java.util.logging.Logger.getLogger("")` (root logger)
2. Each `LogRecord` is checked against `ignore-patterns` (suppressed) then `alert-patterns` (trigger)
3. Matching entries are stored in a ring buffer (`MAX_CAPTURED = 200`)
4. Optionally written to `plugins/ITPlugin/errors.log`
5. A Bukkit task sends in-game alerts to all online players with `itplugin.staff`

**`ErrorEntry` record fields:** `timestamp`, `level`, `serverName`, `source`, `message`, `fullText`

**Config:** `console-monitor.*` section in `config.yml`

---

### 6.4 In-Game Console Terminal

**Files:** `monitor/ConsoleReader.java`, `gui/ConsoleTerminalGUI.java`, `command/ITConsoleCommand.java`

Also extends `java.util.logging.Handler`. Captures **all** log output (not just errors) into a ring buffer (default: 500 lines, configurable via `console-terminal.max-lines`).

**Log levels:** `ALL`, `INFO`, `WARNING`, `SEVERE`

**Terminal GUI features:**
- Paginated log view (most recent lines shown first)
- Level filter toggle button cycles through: ALL → INFO → WARNING → SEVERE → ALL
- Run Command button → chat prompt → dispatches as console sender
- Export to Written Book (`/itconsole book`)
- Clear buffer button
- Blocked commands list in config (default: `stop`, `restart`, `reload`)
- Audit log of every command executed (`plugins/ITPlugin/commands.log`)

---

### 6.5 Cross-Server Monitor

**Files:** `monitor/ServerMonitor.java`

Legacy BungeeCord plugin messaging channel monitor. Periodically sends `PlayerCount` and `GetServer` requests via the `BungeeCord` channel. Requires at least one online player to function (BungeeCord limitation).

**Config:** `server-monitor.*` section — tracked server names must match BungeeCord config exactly.

Also measures **local TPS** via a 100-tick counting window.

---

### 6.6 Trust-Based HTTP Monitor

**Files:** `monitor/MonitorHttpServer.java`, `monitor/MonitorClient.java`, `monitor/TrustManager.java`, `command/ITAdminCommand.java` (trust subcommands)

No BungeeCord required. Any two Spigot servers with ITPlugin can monitor each other.

**Setup flow:**
```
Hub server:    /itadmin trust generate <nick>
               → auto-starts HTTP server on port 28080
               → prints: /itadmin trust connect http://<ip>:28080 <token> <nick>

Remote server: /itadmin trust connect http://<ip>:28080 <token> <nick>
               → verifies token via HTTP ping + probe heartbeat
               → saves config, starts MonitorClient (heartbeat every 30s)

Hub:           /itadmin monitor  or  /itadmin trust status
               → shows live player count, TPS, error count, last-seen time
```

**Token security:**
- Each remote gets a unique UUID token stored in `plugins/ITPlugin/trusted-servers.yml`
- A leaked token only allows that server to report its own harmless stats
- Revoke any time: `/itadmin trust revoke <nick>`

**Heartbeat payload (JSON):**
```json
{
  "token": "<uuid>",
  "name": "<serverNick>",
  "players": 12,
  "tps": 19.8,
  "errors": 3,
  "version": "1.21.4",
  "uptimeSeconds": 3600
}
```

---

### 6.7 Cloud Relay Monitor

**Files:** `monitor/CloudMonitorClient.java`, `monitor/CloudMonitorPoller.java`

Routes heartbeats through an external Plugin Studio API endpoint instead of direct server-to-server HTTP. Ideal for shared hosting (SparkHost, Apex, etc.) where port-forwarding is unavailable.

**Setup (run on every server):**
```
/itadmin cloud setup <api-url> <api-key> <this-server-nick>
```

**Config:** `monitor.cloud.*` section

---

### 6.8 Knowledge Base (Notes)

**Files:** `manager/NoteManager.java`, `gui/KnowledgeBaseGUI.java`, `command/ITNotesCommand.java`

**Storage:** `plugins/ITPlugin/notes.yml`

**Note model:**
```java
record Note(int id, String title, String body, String author, long createdAt)
```

**In-game authoring workflow:**
1. Player runs `/itnotes add` or clicks **Add Article** in the GUI
2. A blank `WRITABLE_BOOK` is added to their inventory
3. Player writes content across pages and clicks **Sign**
4. `PlayerEditBookEvent` is captured → book title becomes note title, all pages joined as body
5. Note is saved, book is removed from inventory

**Search:** case-insensitive substring match on title + body

---

### 6.9 Config Validator

**Files:** `util/ConfigValidator.java`, `command/ITAdminCommand.java` (`validate` case)

Recursively scans the `plugins/` directory (configurable) for `config.yml` and `*.yml` files inside each plugin folder. Attempts to parse each as `YamlConfiguration` and reports:
- **Errors** — YAML parse failures
- **Warnings** — empty files, null top-level values (when `report-warnings: true`)

Accessible via `/itadmin validate` (command) or the **Config Validator** button in the Main Menu GUI.

---

### 6.10 LuckPerms Integration

**File:** `manager/LuckPermsManager.java`

Pure reflection — no compile-time dependency on LuckPerms API. Hooks into LuckPerms via `ServicesManager` at startup.

**Capabilities:**

| Method | With LuckPerms | Without LuckPerms |
|---|---|---|
| `hasPermission(player, perm)` | `player.hasPermission()` (LP hooks in) | `player.hasPermission()` |
| `isStaff(player)` | Checks `itplugin.staff` or `itplugin.admin` | Same |
| `isAdmin(player)` | Checks `itplugin.admin` | Same |
| `getPrefix(player)` | LP meta prefix, color-translated | `""` (empty) |
| `getPrimaryGroup(player)` | LP primary group name | `"default"` |
| `addPermission(player, perm)` | LP persistent node add | No-op |
| `removePermission(player, perm)` | LP persistent node remove | No-op |

---

## 7. Data Flow

### Ticket Creation (GUI path)
```
Player clicks "Create Ticket" in MainMenuGUI
  → GUIListener.handleMainMenu() slot == slotCreate()
  → Closes inventory, registers PendingInput(CREATE_TICKET)
  → Sends chat prompt message
  → Player types message in chat
  → GUIListener.onChat() intercepts (event cancelled)
  → Bukkit.runTask() → TicketManager.createTicket()
  → Staff alert sent to online staff
  → MainMenuGUI reopened
```

### Console Error Alert
```
Server logs ERROR/SEVERE
  → ConsoleMonitor.publish(LogRecord)
  → Pattern match → stored in capturedEntries ring buffer
  → Optional file write → plugins/ITPlugin/errors.log
  → Bukkit.runTask() → send alert to all isStaff() players
```

### Trust Heartbeat (hub receives)
```
Remote server MonitorClient → HTTP POST /itplugin/heartbeat (JSON)
  → MonitorHttpServer validates token via TrustManager.validateToken()
  → TrustManager.recordHeartbeat(nick, snapshot)
  → /itadmin trust status / monitor reads TrustManager.getHeartbeat(nick)
```

---

## 8. Configuration Reference

### `config.yml` Top-Level Sections

| Section | Purpose |
|---|---|
| `helpdesk.*` | Ticket limits, ID prefix, staff notifications |
| `console-monitor.*` | Error capture — patterns, file logging, staff alerts |
| `console-terminal.*` | Log buffer size, blocked commands, audit log |
| `server-monitor.*` | BungeeCord tracked servers, poll interval |
| `monitor.http.*` | Trust HTTP server — enabled flag, port |
| `monitor.client.*` | Trust HTTP client — URL, token, server name (auto-set) |
| `monitor.cloud.*` | Cloud relay — API URL, key, nick, intervals |
| `config-validator.*` | Scan directories, report warnings toggle |

### `messages.yml` Placeholder Format

- `{prefix}` — `&8[&bIT&8]&r `
- `{prefix-warn}` — `&8[&eIT&8]&r `
- `{prefix-error}` — `&8[&cIT&8]&r `
- `{0}`, `{1}`, `{2}` — positional arguments (passed to `MessageManager.get(key, args...)`)
- Standard `&` color codes supported everywhere

---

## 9. Startup & Shutdown Sequence

### `onEnable()`
```
1. saveDefaultConfig()
2. MessageManager        — load messages.yml
3. LuckPermsManager      — hook LuckPerms via reflection
4. TicketManager         — load tickets.yml
5. NoteManager           — load notes.yml
6. TrustManager          — load trusted-servers.yml
7. ConsoleMonitor        — attach to root JUL logger (if enabled)
8. ConsoleReader         — attach to root JUL logger (if enabled)
9. ServerMonitor         — register BungeeCord channels, start poll task (if enabled)
10. MonitorHttpServer    — start embedded HTTP server (if monitor.http.enabled)
11. MonitorClient        — start heartbeat sender (if monitor.client.enabled)
12. CloudMonitorClient   — start cloud push (if monitor.cloud.enabled)
13. CloudMonitorPoller   — start cloud pull (if monitor.cloud.enabled)
14. GUIListener          — register events
15. PlayerListener       — register events
16. Register all 7 commands
```

### `onDisable()`
```
1. ConsoleMonitor.unregister()    — detach JUL handler
2. ConsoleReader.unregister()     — detach JUL handler
3. ServerMonitor.stop()           — cancel tasks, unregister channels
4. MonitorHttpServer.stop()       — stop HTTP server
5. MonitorClient.stop()           — stop heartbeat scheduler
6. CloudMonitorClient.stop()      — stop cloud push
7. CloudMonitorPoller.stop()      — stop cloud pull
8. TicketManager.save()           — persist tickets to disk
9. TrustManager.save()            — persist trusted-servers.yml
```

---

## 10. Build & Deploy

### Prerequisites
- Java 17+
- Maven 3.8+
- Internet access (Spigot API fetched from SpigotMC Nexus)

### Build
```bash
mvn clean package
# Output: output/ITPlugin-1.0.0.jar
```

### Deploy
```bash
cp output/ITPlugin-1.0.0.jar /path/to/server/plugins/
# Restart server or use a plugin manager
```

### In-Server Reload (config only — no full restart needed)
```
/itadmin reload
```
> ⚠️ Reload only refreshes `config.yml` and `messages.yml`. Java code changes require a full server restart.

### Runtime Commands (no restart required)
```
/itadmin trust generate <nick>           → Start HTTP server + issue token
/itadmin trust connect <url> <tok> <n>  → Connect to hub immediately
/itadmin cloud setup <url> <key> <nick> → Configure + start cloud relay
```

---

*Generated by Plugin Studio · ITPlugin v1.0.0*
