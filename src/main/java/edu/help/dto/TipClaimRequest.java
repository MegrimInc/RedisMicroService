package edu.help.dto;

import lombok.Data;

@Data
public class TipClaimRequest {
    private int merchantId;
    private String claimer;
    private String email; // Optional
    private String terminal;        // Terminal's terminal identifier

    public TipClaimRequest(int merchantId, String claimer, String email, String terminal) {
        this.merchantId = merchantId;
        this.claimer = claimer;
        this.email = email;
        this.terminal = terminal;
    }
}