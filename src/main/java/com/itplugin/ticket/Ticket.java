package com.itplugin.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Ticket {

    private final int id;
    private final String submitterName;
    private final UUID submitterUuid;
    private final String message;
    private final long createdAt;
    private TicketStatus status;
    private String assignee;
    private final List<String> comments;

    public Ticket(int id, String submitterName, UUID submitterUuid, String message) {
        this.id = id;
        this.submitterName = submitterName;
        this.submitterUuid = submitterUuid;
        this.message = message;
        this.createdAt = Instant.now().getEpochSecond();
        this.status = TicketStatus.OPEN;
        this.assignee = null;
        this.comments = new ArrayList<>();
    }

    public Ticket(int id, String submitterName, UUID submitterUuid, String message,
                  long createdAt, TicketStatus status, String assignee, List<String> comments) {
        this.id = id;
        this.submitterName = submitterName;
        this.submitterUuid = submitterUuid;
        this.message = message;
        this.createdAt = createdAt;
        this.status = status;
        this.assignee = assignee;
        this.comments = new ArrayList<>(comments);
    }

    public int getId() { return id; }
    public String getSubmitterName() { return submitterName; }
    public UUID getSubmitterUuid() { return submitterUuid; }
    public String getMessage() { return message; }
    public long getCreatedAt() { return createdAt; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public List<String> getComments() { return comments; }
    public void addComment(String comment) { this.comments.add(comment); }
}
