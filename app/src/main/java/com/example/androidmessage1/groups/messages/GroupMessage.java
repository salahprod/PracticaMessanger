package com.example.androidmessage1.groups.messages;

public class GroupMessage {
    private String message_id;
    private String ownerId;
    private String text;
    private String date;
    private String senderName;
    private String senderAvatar;
    private String messageType;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Long timestamp;
    private Boolean isRead;
    private String fileThumbnail;

    public GroupMessage() {
        // Default constructor required for Firebase
        this.messageType = "text"; // Default type
        this.isRead = false;
        this.timestamp = System.currentTimeMillis();
    }

    public GroupMessage(String message_id, String ownerId, String text, String date) {
        this.message_id = message_id;
        this.ownerId = ownerId;
        this.text = text;
        this.date = date;
        this.messageType = "text";
        this.isRead = false;
        this.timestamp = System.currentTimeMillis();
    }

    public GroupMessage(String message_id, String ownerId, String text, String date, String messageType) {
        this.message_id = message_id;
        this.ownerId = ownerId;
        this.text = text;
        this.date = date;
        this.messageType = messageType != null ? messageType : "text";
        this.isRead = false;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getMessage_id() {
        return message_id;
    }

    public void setMessage_id(String message_id) {
        this.message_id = message_id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAvatar() {
        return senderAvatar;
    }

    public void setSenderAvatar(String senderAvatar) {
        this.senderAvatar = senderAvatar;
    }

    public String getMessageType() {
        return messageType != null ? messageType : "text";
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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getTimestamp() {
        return timestamp != null ? timestamp : System.currentTimeMillis();
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getIsRead() {
        return isRead != null ? isRead : false;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public String getFileThumbnail() {
        return fileThumbnail;
    }

    public void setFileThumbnail(String fileThumbnail) {
        this.fileThumbnail = fileThumbnail;
    }

    // Utility methods
    public boolean isTextMessage() {
        return "text".equals(getMessageType());
    }

    public boolean isImageMessage() {
        return "image".equals(getMessageType());
    }

    public boolean isVideoMessage() {
        return "video".equals(getMessageType());
    }

    public boolean isFileMessage() {
        return "file".equals(getMessageType());
    }

    public boolean hasFile() {
        return fileUrl != null && !fileUrl.isEmpty();
    }

    public String getDisplayText() {
        if (isTextMessage()) {
            return text;
        } else if (isImageMessage()) {
            return "📷 Photo";
        } else if (isVideoMessage()) {
            return "🎥 Video";
        } else if (isFileMessage()) {
            if (fileName != null && !fileName.isEmpty()) {
                return "📎 File: " + fileName;
            } else {
                return "📎 File";
            }
        }
        return text != null ? text : "";
    }

    public String getFileExtension() {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }

    public boolean isImageFile() {
        if (isImageMessage()) return true;
        String extension = getFileExtension();
        return extension.equals("jpg") || extension.equals("jpeg") ||
                extension.equals("png") || extension.equals("gif") ||
                extension.equals("bmp") || extension.equals("webp");
    }

    public boolean isVideoFile() {
        if (isVideoMessage()) return true;
        String extension = getFileExtension();
        return extension.equals("mp4") || extension.equals("avi") ||
                extension.equals("mov") || extension.equals("mkv") ||
                extension.equals("webm") || extension.equals("3gp");
    }

    public String getReadableFileSize() {
        if (fileSize == null) return "Unknown size";

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public boolean isFromUser(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public void markAsRead() {
        this.isRead = true;
    }

    @Override
    public String toString() {
        return "GroupMessage{" +
                "message_id='" + message_id + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", text='" + text + '\'' +
                ", date='" + date + '\'' +
                ", senderName='" + senderName + '\'' +
                ", senderAvatar='" + senderAvatar + '\'' +
                ", messageType='" + messageType + '\'' +
                ", fileUrl='" + fileUrl + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", timestamp=" + timestamp +
                ", isRead=" + isRead +
                ", fileThumbnail='" + fileThumbnail + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupMessage that = (GroupMessage) o;

        return message_id != null ? message_id.equals(that.message_id) : that.message_id == null;
    }

    @Override
    public int hashCode() {
        return message_id != null ? message_id.hashCode() : 0;
    }

    /**
     * Creates a file message
     */
    public static GroupMessage createFileMessage(String messageId, String ownerId, String fileUrl,
                                                 String fileName, String messageType, String date) {
        GroupMessage message = new GroupMessage(messageId, ownerId, "", date);
        message.setMessageType(messageType);
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);

        // Set appropriate text based on message type
        switch (messageType) {
            case "image":
                message.setText("📷 Photo");
                break;
            case "video":
                message.setText("🎥 Video");
                break;
            case "file":
                message.setText("📎 File: " + (fileName != null ? fileName : "File"));
                break;
            default:
                message.setText("📎 File");
        }

        return message;
    }

    /**
     * Creates a text message
     */
    public static GroupMessage createTextMessage(String messageId, String ownerId, String text, String date) {
        return new GroupMessage(messageId, ownerId, text, date);
    }

    /**
     * Creates a message with sender info
     */
    public static GroupMessage createMessageWithSender(String messageId, String ownerId, String text,
                                                       String date, String senderName, String senderAvatar) {
        GroupMessage message = new GroupMessage(messageId, ownerId, text, date);
        message.setSenderName(senderName);
        message.setSenderAvatar(senderAvatar);
        return message;
    }
}