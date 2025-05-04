package edu.help.dto;

import java.io.Serializable;

public class StationSession implements Serializable {
    private int merchantId;
    private String stationId;
    private String sessionId;
    private boolean active;

    // Default constructor
    public StationSession() {
    }

    // Constructor with parameters
    public StationSession(int merchantId, String stationId, String sessionId, boolean active) {
        this.merchantId = merchantId;
        this.stationId = stationId;
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

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
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
        return "StationSession{" +
                "merchantId=" + merchantId +
                ", stationId='" + stationId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", active=" + active +
                '}';
    }
}