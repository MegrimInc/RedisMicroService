package edu.help.dto;

import java.util.List;

public class OrderResponse {
    private String message, name, messageType;
    private double totalPrice, totalGratuity, totalServiceFee, totalTax;
    private List<ItemOrder> items;
    private int totalPointPrice;
    // And also we will need to check on the frontend how it works.
    public OrderResponse() {
        // Default constructor
    }

    public OrderResponse(String name, String message, double totalPrice, double totalGratuity, double totalServiceFee, double totalTax, List<ItemOrder> items, String messageType, int totalPointPrice) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.totalGratuity = totalGratuity; // Updated initialization
        this.totalServiceFee = totalServiceFee;
        this.totalTax = totalTax;
        this.items = items;
        this.messageType = messageType;
        this.totalPointPrice = totalPointPrice;
        this.name = name;
    }

    public OrderResponse(String name, String message, double totalPrice,  double totalGratuity, double totalServiceFee, double totalTax, List<ItemOrder> items, int totalPointPrice) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.totalGratuity = totalGratuity; // Updated initialization
        this.totalServiceFee = totalServiceFee;
        this.totalTax = totalTax;
        this.items = items;
        this.messageType = "";
        this.totalPointPrice = totalPointPrice;
        this.name = name;
    }

    

    public String getName() { return name; };

    public void setName(String name) { this.name = name; }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public double getTotalGratuity() {
        return totalGratuity;
    }

    public void setTotalGratuity(double totalGratuity) {
        this.totalGratuity = totalGratuity;
    }


     public double getTotalServiceFee() {
        return totalServiceFee;
    }

    public void setTotalServiceFee(double totalServiceFee) {
        this.totalServiceFee = totalServiceFee;
    }

     public double getTotalTax() {
        return totalTax;
    }

    public void setTotalTax(double totalTax) {
        this.totalTax = totalTax;
    }

    public List<ItemOrder> getItems() {
        return items;
    }

    public void setItems(List<ItemOrder> items) {
        this.items = items;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public int getTotalPointPrice() {
        return totalPointPrice;
    }

    public void setTotalPointPrice(int totalPointPrice) {
        this.totalPointPrice = totalPointPrice;
    }

    // Inner class representing a item order
    // Updated ItemOrder inner class with all required fields
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

        // Getters and Setters
        public int getItemId() {
            return itemId;
        }

        public void setItemId(int itemId) {
            this.itemId = itemId;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getPaymentType() {
            return paymentType;
        }

        public void setPaymentType(String paymentType) {
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
