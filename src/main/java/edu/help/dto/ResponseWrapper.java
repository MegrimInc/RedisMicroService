package edu.help.dto;

public class ResponseWrapper {
    private String messageType;
    private Object data;
    private String message;

    public ResponseWrapper(String messageType, Object data, String message) {
        this.messageType = messageType;
        this.data = data;
        this.message = message;
    }

    // Getters and setters
    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
