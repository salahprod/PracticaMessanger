package com.example.androidmessage1.groups.members;

public class GroupMember {
    private String userId;
    private String username;
    private String profileImage;
    private String role; // "owner", "admin", "member"
    private String status;

    public GroupMember() {
        // Пустой конструктор для Firebase
    }

    public GroupMember(String userId, String username, String profileImage, String role, String status) {
        this.userId = userId;
        this.username = username;
        this.profileImage = profileImage;
        this.role = role;
        this.status = status;
    }

    // Геттеры и сеттеры
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}