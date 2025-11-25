package com.example.androidmessage1.groups.messages;

public class GroupMessage {
    private String message_id;
    private String ownerId;
    private String text;
    private String date;
    private String senderName;
    private String senderAvatar;

    public GroupMessage() {
        // Default constructor required for Firebase
    }

    public GroupMessage(String message_id, String ownerId, String text, String date) {
        this.message_id = message_id;
        this.ownerId = ownerId;
        this.text = text;
        this.date = date;
    }

    // Getters and Setters
    public String getMessage_id() { return message_id; }
    public void setMessage_id(String message_id) { this.message_id = message_id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderAvatar() { return senderAvatar; }
    public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }
}