package edu.help.dto;

import lombok.Data;

@Data
public class TipClaimRequest {
    private int merchantId;
    private String terminalName;
    private String terminalEmail; // Optional
    private String terminal;        // Terminal's terminal identifier

    public TipClaimRequest(int merchantId, String terminalName, String terminalEmail1, String terminal) {
        this.merchantId = merchantId;
        this.terminalName = terminalName;
        this.terminalEmail = terminalEmail1;
        this.terminal = terminal;
    }
}