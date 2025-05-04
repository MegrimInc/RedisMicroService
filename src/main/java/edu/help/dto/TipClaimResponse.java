package edu.help.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TipClaimResponse {
    private String merchantEmail;
    private List<PostgresOrder> orders;

    public TipClaimResponse(String merchantEmail)
    {
        this.merchantEmail = merchantEmail;
    }
}
