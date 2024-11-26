package edu.help.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TipClaimResponse {
    private String barEmail;
    private List<PostgresOrder> orders;

    public TipClaimResponse(String barEmail)
    {
        this.barEmail = barEmail;
    }
}

