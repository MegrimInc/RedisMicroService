package edu.help.dto;

import lombok.Data;

@Data
public class TipClaimRequest {
    private int barId;
    private String bartenderName;
    private String bartenderEmail; // Optional
    private String station;        // Bartender's station identifier

    public TipClaimRequest(int barID, String bartenderName, String bartenderEmail1, String station) {
        this.barId = barID;
        this.bartenderName = bartenderName;
        this.bartenderEmail = bartenderEmail1;
        this.station = station;
    }
}

