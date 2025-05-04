package edu.help.dto;

import java.io.Serializable;

public class TerminalSession implements Serializable {
    private int merchantId;
    private String terminalId;
    private String sessionId;
    private boolean active;

    // Default constructor
    public TerminalSession() {
    }

    // Constructor with parameters
    public TerminalSession(int merchantId, String terminalId, String sessionId, boolean active) {
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.sessionId = sessionId;
        this.active = active;
    }

    // Getters and Setters
    public int getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(int merchantId) {
        this.merchantId = merchantId;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
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
        return "TerminalSession{" +
                "merchantId=" + merchantId +
                ", terminalId='" + terminalId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", active=" + active +
                '}';
    }
}