package com.example.androidmessage1.chats;

public class Chat {
    private String chat_id;
    private String other_user_id;
    private String current_user_id;
    private String chat_name;
    private String lastMessage;
    private String lastMessageTime;
    private long lastMessageTimestamp;
    private int unreadCount;
    private boolean isGroup;
    private int membersCount;

    public Chat() {
        // Default constructor required for Firebase
    }

    public Chat(String chat_id, String other_user_id, String current_user_id, String chat_name) {
        this.chat_id = chat_id;
        this.other_user_id = other_user_id;
        this.current_user_id = current_user_id;
        this.chat_name = chat_name;
        this.lastMessage = "";
        this.lastMessageTime = "";
        this.lastMessageTimestamp = 0L;
        this.unreadCount = 0;
        this.isGroup = false;
        this.membersCount = 0;
    }

    // Getters and Setters
    public String getChat_id() { return chat_id; }
    public void setChat_id(String chat_id) { this.chat_id = chat_id; }

    public String getOther_user_id() { return other_user_id; }
    public void setOther_user_id(String other_user_id) { this.other_user_id = other_user_id; }

    public String getCurrent_user_id() { return current_user_id; }
    public void setCurrent_user_id(String current_user_id) { this.current_user_id = current_user_id; }

    public String getChat_name() { return chat_name; }
    public void setChat_name(String chat_name) { this.chat_name = chat_name; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(String lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public int getMembersCount() { return membersCount; }
    public void setMembersCount(int membersCount) { this.membersCount = membersCount; }
}