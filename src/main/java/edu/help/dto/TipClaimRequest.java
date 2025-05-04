package edu.help.dto;

import lombok.Data;

@Data
public class TipClaimRequest {
    private int merchantId;
    private String terminalName;
    private String terminalEmail; // Optional
    private String terminal;        // Terminal's terminal identifier

    public TipClaimRequest(int merchantID, String terminalName, String terminalEmail1, String terminal) {
        this.merchantId = merchantID;
        this.terminalName = terminalName;
        this.terminalEmail = terminalEmail1;
        this.terminal = terminal;
    }
}