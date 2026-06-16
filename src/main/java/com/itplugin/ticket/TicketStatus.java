package com.itplugin.ticket;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED;

    public String displayName() {
        return switch (this) {
            case OPEN -> "§aOPEN";
            case IN_PROGRESS -> "§eIN PROGRESS";
            case CLOSED -> "§cCLOSED";
        };
    }
}
