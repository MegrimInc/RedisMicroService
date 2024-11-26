package edu.help.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostgresOrder {

    private int orderId; // Unique identifier for each order
    private int barId; // ID of the bar where the order was placed
    private int userId; // ID of the user who placed the order
    private Instant timestamp; // Timestamp when the order was completed
    private List<DrinkOrderDTO> drinks; // List of drink orders
    private int totalPointPrice; // Total price in points if used for payment
    private double totalRegularPrice; // Total price in dollars
    private double tip; // Tip amount given by the user for the order
    private boolean inAppPayments; // Indicates if the payment was made in-app
    private String status; // Final status of the order ('claimed', 'delivered', 'canceled')
    private String station; // Bartender station identifier (A-Z)
    private String tipsClaimed; // "NULL" (as a string) if not claimed, or the bartender's name

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrinkOrderDTO {
        private int drinkId;
        private String drinkName;
        private String paymentType;
        private String sizeType;
        private int quantity;
    }
}
