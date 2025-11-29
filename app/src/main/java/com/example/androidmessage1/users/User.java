package com.example.androidmessage1.users;

import android.graphics.Color;

public class User {
    private String username;
    private String profileImage;
    private String lastMessage;
    private int unreadCount;
    private String userId;
    private int statusColor;
    private Boolean isOnline; // Добавляем поле для онлайн статуса
    private Long lastOnline; // Добавляем поле для времени последней активности

    public User() {
        // Default constructor
        this.lastMessage = "";
        this.unreadCount = 0;
        this.userId = "";
        this.statusColor = Color.GRAY;
        this.isOnline = false;
        this.lastOnline = 0L;
    }

    public User(String username, String profileImage) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = "";
        this.unreadCount = 0;
        this.userId = "";
        this.statusColor = Color.GRAY;
        this.isOnline = false;
        this.lastOnline = 0L;
    }

    public User(String username, String profileImage, String lastMessage, int unreadCount) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
        this.userId = "";
        this.statusColor = Color.GRAY;
        this.isOnline = false;
        this.lastOnline = 0L;
    }

    public User(String username, String profileImage, String lastMessage, int unreadCount, String userId, int statusColor) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
        this.userId = userId;
        this.statusColor = statusColor;
        this.isOnline = false;
        this.lastOnline = 0L;
    }

    // Новый конструктор с онлайн статусом
    public User(String username, String profileImage, String lastMessage, int unreadCount, String userId, int statusColor, Boolean isOnline, Long lastOnline) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
        this.userId = userId;
        this.statusColor = statusColor;
        this.isOnline = isOnline;
        this.lastOnline = lastOnline;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getStatusColor() {
        return statusColor;
    }

    public void setStatusColor(int statusColor) {
        this.statusColor = statusColor;
    }

    // Новые геттеры и сеттеры для онлайн статуса
    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean isOnline) {
        this.isOnline = isOnline;
    }

    public Long getLastOnline() {
        return lastOnline;
    }

    public void setLastOnline(Long lastOnline) {
        this.lastOnline = lastOnline;
    }

    // Метод для проверки онлайн статуса
    public boolean isCurrentlyOnline() {
        if (isOnline != null && isOnline) {
            return true;
        }

        if (lastOnline != null) {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastOnline;
            return timeDiff < 30000; // 30 секунд
        }

        return false;
    }
}