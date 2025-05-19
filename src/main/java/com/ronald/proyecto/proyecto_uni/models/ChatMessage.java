package com.ronald.proyecto.proyecto_uni.models;

import java.time.LocalDateTime;

public class ChatMessage {
    private String content;
    private String sender;
    private MessageType type;
    private LocalDateTime timestamp;

    public enum MessageType {
        CHAT, JOIN, LEAVE
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    
}
