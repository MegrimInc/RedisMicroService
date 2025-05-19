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
    private int merchantId; // Id of the merchant where the order was placed
    private int customerId; // Id of the customer who placed the order
    private Instant timestamp; // Timestamp when the order was completed
    private List<ItemOrderDTO> items; // List of item orders
    private int totalPointPrice; // Total price in points if used for payment
    private double totalRegularPrice; // Total price in dollars
    private double totalGratuity; // Tip amount given by the customer for the order
    private double totalServiceFee;
      private double totalTax;
    private boolean inAppPayments; // Indicates if the payment was made in-app
    private String status; // Final status of the order ('claimed', 'delivered', 'canceled')
    private String terminal; // Terminal terminal identifier (A-Z)
    private String claimer; // "NULL" (as a string) if not claimed, or the terminal's name

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemOrderDTO {
        private int itemId;
        private String itemName;
        private String paymentType;
        private int quantity;
    }
}