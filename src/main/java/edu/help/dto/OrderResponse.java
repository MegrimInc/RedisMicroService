package edu.help.dto;

import java.util.List;

public class OrderResponse {
    private String message, name, messageType;
    private double totalPrice, tip;
    private List<DrinkOrder> drinks;
    private int totalPointPrice;
    // And also we will need to check on the frontend how it works.
    public OrderResponse() {
        // Default constructor
    }

    public OrderResponse(String name, String message, double totalPrice, double tip, List<DrinkOrder> drinks, String messageType, int totalPointPrice) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.tip = tip; // Updated initialization
        this.drinks = drinks;
        this.messageType = messageType;
        this.totalPointPrice = totalPointPrice;
        this.name = name;
    }

    public OrderResponse(String name, String message, double totalPrice, double tip, List<DrinkOrder> drinks, int totalPointPrice) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.tip = tip; // Updated initialization
        this.drinks = drinks;
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

    public double getTip() {
        return tip;
    }

    public void setTip(double tip) {
        this.tip = tip;
    }

    public List<DrinkOrder> getDrinks() {
        return drinks;
    }

    public void setDrinks(List<DrinkOrder> drinks) {
        this.drinks = drinks;
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

    // Inner class representing a drink order
    // Updated DrinkOrder inner class with all required fields
    public static class DrinkOrder {
        private int drinkId;
        private String drinkName;
        private int quantity;
        private String paymentType; // "points" or "regular"
        private String sizeType; // "single", "double", or empty for drinks without size variations

        public DrinkOrder(int drinkId, String drinkName, int quantity, String paymentType, String sizeType) {
            this.drinkId = drinkId;
            this.drinkName = drinkName;
            this.quantity = quantity;
            this.paymentType = paymentType;
            this.sizeType = sizeType;
        }

        // Getters and Setters
        public int getDrinkId() {
            return drinkId;
        }

        public void setDrinkId(int drinkId) {
            this.drinkId = drinkId;
        }

        public String getDrinkName() {
            return drinkName;
        }

        public void setDrinkName(String drinkName) {
            this.drinkName = drinkName;
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

        public String getSizeType() {
            return sizeType;
        }

        public void setSizeType(String sizeType) {
            this.sizeType = sizeType;
        }

        @Override
        public String toString() {
            return "DrinkOrder{" +
                    "drinkId=" + drinkId +
                    ", drinkName='" + drinkName + '\'' +
                    ", quantity=" + quantity +
                    ", paymentType='" + paymentType + '\'' +
                    ", sizeType='" + sizeType + '\'' +
                    '}';
        }
    }
}
