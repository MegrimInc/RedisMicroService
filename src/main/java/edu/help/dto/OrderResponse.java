package edu.help.dto;
import lombok.Data;
import java.util.List;

@Data
public class OrderResponse {
    private String message, name, messageType;
    private double totalPrice, totalGratuity, totalServiceFee, totalTax;
    private List<ItemOrder> items;
    private int totalPointPrice;
    private boolean inAppPayments;

    // And also we will need to check on the frontend how it works.
    public OrderResponse() {
        // Default constructor
    }

    public OrderResponse(String name, String message, double totalPrice, double totalGratuity, double totalServiceFee,
            double totalTax, List<ItemOrder> items, String messageType, int totalPointPrice, boolean inAppPayments) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.totalGratuity = totalGratuity; // Updated initialization
        this.totalServiceFee = totalServiceFee;
        this.totalTax = totalTax;
        this.items = items;
        this.messageType = messageType;
        this.totalPointPrice = totalPointPrice;
        this.name = name;
        this.inAppPayments = inAppPayments;
    }

    public OrderResponse(String name, String message, double totalPrice, double totalGratuity, double totalServiceFee,
            double totalTax, List<ItemOrder> items, int totalPointPrice, boolean inAppPayments) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.totalGratuity = totalGratuity; // Updated initialization
        this.totalServiceFee = totalServiceFee;
        this.totalTax = totalTax;
        this.items = items;
        this.messageType = "";
        this.totalPointPrice = totalPointPrice;
        this.name = name;
        this.inAppPayments = inAppPayments;
    }

    // Inner class representing a item order
    // Updated ItemOrder inner class with all required fields
    @Data
    public static class ItemOrder {
        private int itemId;
        private String itemName;
        private int quantity;
        private String paymentType; // "points" or "regular"

        public ItemOrder(int itemId, String itemName, int quantity, String paymentType) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.paymentType = paymentType;
        }

        @Override
        public String toString() {
            return "ItemOrder{" +
                    "itemId=" + itemId +
                    ", itemName='" + itemName + '\'' +
                    ", quantity=" + quantity +
                    ", paymentType='" + paymentType + '\'' +
                    '}';
        }
    }
}
