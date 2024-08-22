package edu.help.dto;

import java.io.Serializable;

public class BartenderSession implements Serializable {
    private int barId;
    private String bartenderId;
    private String sessionId;
    private boolean active;

    // Default constructor
    public BartenderSession() {
    }

    // Constructor with parameters
    public BartenderSession(int barId, String bartenderId, String sessionId, boolean active) {
        this.barId = barId;
        this.bartenderId = bartenderId;
        this.sessionId = sessionId;
        this.active = active;
    }

    // Getters and Setters
    public int getBarId() {
        return barId;
    }

    public void setBarId(int barId) {
        this.barId = barId;
    }

    public String getBartenderId() {
        return bartenderId;
    }

    public void setBartenderId(String bartenderId) {
        this.bartenderId = bartenderId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "BartenderSession{" +
                "barId=" + barId +
                ", bartenderId='" + bartenderId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", active=" + active +
                '}';
    }
}