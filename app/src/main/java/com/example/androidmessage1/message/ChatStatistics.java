package com.example.androidmessage1.message;

import java.util.Date;

public class ChatStatistics {
    private String chatId;
    private String chatName;
    private long totalTimeSpent; // в миллисекундах
    private Date lastActivity;
    private boolean isGroup;
    private int messageCount;

    public ChatStatistics() {
        // Default constructor for Firebase
    }

    public ChatStatistics(String chatId, String chatName, boolean isGroup) {
        this.chatId = chatId;
        this.chatName = chatName;
        this.totalTimeSpent = 0;
        this.lastActivity = new Date();
        this.isGroup = isGroup;
        this.messageCount = 0;
    }

    // Getters and Setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getChatName() { return chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }

    public long getTotalTimeSpent() { return totalTimeSpent; }
    public void setTotalTimeSpent(long totalTimeSpent) { this.totalTimeSpent = totalTimeSpent; }

    public Date getLastActivity() { return lastActivity; }
    public void setLastActivity(Date lastActivity) { this.lastActivity = lastActivity; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public void addTimeSpent(long timeInMillis) {
        this.totalTimeSpent += timeInMillis;
        this.lastActivity = new Date();
    }

    public void incrementMessageCount() {
        this.messageCount++;
        this.lastActivity = new Date();
    }

    // Форматирование времени в читаемый формат
    public String getFormattedTime() {
        long seconds = totalTimeSpent / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%02d:%02d", minutes, secs);
        } else {
            return String.format("00:%02d", secs);
        }
    }
}