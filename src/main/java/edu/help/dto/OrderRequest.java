package edu.help.dto;

import java.util.List;

public class OrderRequest {

    private int barId;
    private int userId;
    private double tip; // Renamed field
    private List<DrinkOrder> drinks;
    private boolean inAppPayments;
    private boolean isHappyHour;



    @Override
    public String toString() {
        return "OrderRequest{" +
                "barId=" + barId +
                ", userId=" + userId +
                ", tip=" + tip + // Updated toString
                ", drinks=" + drinks +
                ", inAppPayments=" + inAppPayments +
                ", isHappyHour=" + isHappyHour + 
                '}';
    }

    // Getters and Setters
    public int getBarId() {
        return barId;
    }

    public void setBarId(int barId) {
        this.barId = barId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public boolean isInAppPayments() {
        return inAppPayments;
    }

    public void setInAppPayments(boolean inAppPayments) {
        this.inAppPayments = inAppPayments;
    }

    public boolean isHappyHour() { // Getter for isHappyHour
        return isHappyHour;
    }

    public void setHappyHour(boolean isHappyHour) { // Setter for isHappyHour
        this.isHappyHour = isHappyHour;
    }

    public static class DrinkOrder {
        private int drinkId;
        private int quantity;
        private String paymentType; // "points" or "regular"
        private String sizeType; // "single" or "double" or empty for drinks without size variations

        @Override
        public String toString() {
            return "DrinkOrder{" +
                    "drinkId=" + drinkId +
                    ", quantity=" + quantity +
                    ", paymentType='" + paymentType + '\'' +
                    ", sizeType='" + sizeType + '\'' +
                    '}';
        }

        // Getters and Setters
        public int getDrinkId() {
            return drinkId;
        }

        public void setDrinkId(int drinkId) {
            this.drinkId = drinkId;
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
    }
}