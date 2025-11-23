package com.example.androidmessage1.users;

public class User {
    String username, profileImage;

    public User() {

    }

    public User(String username, String profileImage) {
        this.username = username;
        this.profileImage = profileImage;
    }

    // ✅ ДОБАВЬ ГЕТТЕРЫ
    public String getUsername() {
        return username;
    }

    public String getProfileImage() {
        return profileImage;
    }

    // ✅ ДОБАВЬ СЕТТЕРЫ
    public void setUsername(String username) {
        this.username = username;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
}