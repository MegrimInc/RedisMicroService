package edu.help.model;

import java.util.List;

public class OrderResponse {

    private String message;
    private double totalPrice;
    private List<DrinkOrder> drinks;

    public OrderResponse(String message, double totalPrice, List<DrinkOrder> drinks) {
        this.message = message;
        this.totalPrice = totalPrice;
        this.drinks = drinks;
    }

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

    public List<DrinkOrder> getDrinks() {
        return drinks;
    }

    public void setDrinks(List<DrinkOrder> drinks) {
        this.drinks = drinks;
    }

    // Inner class representing a drink order
    public static class DrinkOrder {
        private int drinkId;
        private String drinkName;
        private int quantity;

        public DrinkOrder(int drinkId, String drinkName, int quantity) {
            this.drinkId = drinkId;
            this.drinkName = drinkName;
            this.quantity = quantity;
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
    }
}
