package com.example.androidmessage1.message;

public class Message {
    private String id, ownerId, text, date;
    private String messageType; // "text", "image", "video", "file"
    private String fileUrl;
    private String fileName;

    public Message(String id, String ownerId, String text, String date) {
        this.id = id;
        this.ownerId = ownerId;
        this.text = text;
        this.date = date;
        this.messageType = "text"; // По умолчанию текстовое сообщение
    }

    // Пустой конструктор для Firebase
    public Message() {
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

    // Новые геттеры и сеттеры для работы с файлами
    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}