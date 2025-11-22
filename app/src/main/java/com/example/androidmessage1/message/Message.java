package com.example.androidmessage1.message;

public class Message {
    private String id, ownerId,text,date;

    public Message(String id, String ownerId, String text, String date) {
        this.id = id;
        this.ownerId = ownerId;
        this.text = text;
        this.date = date;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getText() {
        return text;
    }

    public String getDate() {
        return date;
    }
}
