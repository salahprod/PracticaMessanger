package com.example.androidmessage1.users;

public class User {
    private String username;
    private String profileImage;
    private String lastMessage;
    private int unreadCount;

    public User() {
        // Default constructor
    }

    public User(String username, String profileImage) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = "";
        this.unreadCount = 0;
    }

    public User(String username, String profileImage, String lastMessage, int unreadCount) {
        this.username = username;
        this.profileImage = profileImage;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}