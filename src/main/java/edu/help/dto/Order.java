package edu.help.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Order {
    private int barId;
    private int userId;
    private double price;
    private List<DrinkOrder> drinks;
    private String status;
    private String claimer;
    private String timestamp;

    public Order(int barId, int userId, double price, List<DrinkOrder> drinks, String status, String claimer, String timestamp) {
        this.barId = barId;
        this.userId = userId;
        this.price = price;
        this.drinks = drinks;
        this.status = status;
        this.claimer = claimer;
        this.timestamp = timestamp;
    }

    // Getters and setters
    @JsonProperty("barId")
    public int getBarId() {
        return barId;
    }

    @JsonProperty("barId")
    public void setBarId(int barId) {
        this.barId = barId;
    }

    @JsonProperty("userId")
    public int getUserId() {
        return userId;
    }

    @JsonProperty("userId")
    public void setUserId(int userId) {
        this.userId = userId;
    }

    @JsonProperty("price")
    public double getPrice() {
        return price;
    }

    @JsonProperty("price")
    public void setPrice(double price) {
        this.price = price;
    }

    @JsonProperty("drinks")
    public List<DrinkOrder> getDrinks() {
        return drinks;
    }

    @JsonProperty("drinks")
    public void setDrinks(List<DrinkOrder> drinks) {
        this.drinks = drinks;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("claimer")
    public String getClaimer() {
        return claimer;
    }

    @JsonProperty("claimer")
    public void setClaimer(String claimer) {
        this.claimer = claimer;
    }

    @JsonProperty("timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    @JsonProperty("timestamp")
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public static class DrinkOrder {
        private int id;
        private String drinkName;
        private int quantity;

        public DrinkOrder(int id, String drinkName, int quantity) {
            this.id = id;
            this.drinkName = drinkName;
            this.quantity = quantity;
        }

        @JsonProperty("id")
        public int getId() {
            return id;
        }

        @JsonProperty("id")
        public void setId(int id) {
            this.id = id;
        }

        @JsonProperty("drinkName")
        public String getDrinkName() {
            return drinkName;
        }

        @JsonProperty("drinkName")
        public void setDrinkName(String drinkName) {
            this.drinkName = drinkName;
        }

        @JsonProperty("quantity")
        public int getQuantity() {
            return quantity;
        }

        @JsonProperty("quantity")
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
