package edu.help.dto;

import lombok.Data;

@Data
public class TipClaimRequest {
    private int merchantId;
    private String stationName;
    private String stationEmail; // Optional
    private String station;        // Station's station identifier

    public TipClaimRequest(int merchantID, String stationName, String stationEmail1, String station) {
        this.merchantId = merchantID;
        this.stationName = stationName;
        this.stationEmail = stationEmail1;
        this.station = station;
    }
}