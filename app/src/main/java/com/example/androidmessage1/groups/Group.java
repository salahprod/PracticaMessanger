package com.example.androidmessage1.groups;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private String groupId;
    private String groupName;
    private String groupImage;
    private String createdBy;
    private long createdAt;
    private List<String> members;
    private String lastMessage;
    private long lastMessageTime;
    private int unreadCount;

    public Group() {
        // Конструктор по умолчанию для Firebase
        this.members = new ArrayList<>();
    }

    public Group(String groupId, String groupName, String groupImage, String createdBy) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupImage = groupImage;
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.members = new ArrayList<>();
        this.lastMessage = "Group created";
        this.lastMessageTime = System.currentTimeMillis();
        this.unreadCount = 0;
    }

    // Getters and Setters
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupImage() { return groupImage; }
    public void setGroupImage(String groupImage) { this.groupImage = groupImage; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<String> getMembers() {
        if (members == null) {
            members = new ArrayList<>();
        }
        return members;
    }

    public void setMembers(List<String> members) {
        if (members == null) {
            this.members = new ArrayList<>();
        } else {
            this.members = members;
        }
    }

    public String getLastMessage() {
        return lastMessage != null ? lastMessage : "Group created";
    }

    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public void addMember(String userId) {
        if (members == null) {
            members = new ArrayList<>();
        }
        if (!members.contains(userId)) {
            members.add(userId);
        }
    }

    public void removeMember(String userId) {
        if (members != null) {
            members.remove(userId);
        }
    }

    public boolean isMember(String userId) {
        return members != null && members.contains(userId);
    }
}