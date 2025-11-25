package com.example.androidmessage1.groups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Group {
    private String groupId;
    private String groupName;
    private String groupImage;
    private String createdBy;
    private long createdAt;
    private List<String> members;
    private String lastMessage;
    private String lastMessageSender;
    private long lastMessageTime;
    private int unreadCount;
    private HashMap<String, String> memberNames;

    public Group() {
        this.members = new ArrayList<>();
        this.memberNames = new HashMap<>();
    }

    public Group(String groupId, String groupName, String groupImage, String createdBy) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupImage = groupImage;
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.members = new ArrayList<>();
        this.memberNames = new HashMap<>();
        this.lastMessage = "Group created";
        this.lastMessageSender = "system";
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
        if (members == null) members = new ArrayList<>();
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members != null ? members : new ArrayList<>();
    }

    public String getLastMessage() {
        return lastMessage != null ? lastMessage : "Group created";
    }

    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getLastMessageSender() {
        return lastMessageSender != null ? lastMessageSender : "system";
    }

    public void setLastMessageSender(String lastMessageSender) {
        this.lastMessageSender = lastMessageSender;
    }

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public HashMap<String, String> getMemberNames() {
        if (memberNames == null) memberNames = new HashMap<>();
        return memberNames;
    }

    public void setMemberNames(HashMap<String, String> memberNames) {
        this.memberNames = memberNames != null ? memberNames : new HashMap<>();
    }

    public void addMember(String userId, String userName) {
        if (!members.contains(userId)) {
            members.add(userId);
        }
        if (userName != null) {
            memberNames.put(userId, userName);
        }
    }

    public void removeMember(String userId) {
        members.remove(userId);
        memberNames.remove(userId);
    }

    public boolean isMember(String userId) {
        return members.contains(userId);
    }
}