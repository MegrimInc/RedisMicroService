package edu.help.dto;

import jakarta.annotation.Nullable;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {
    private int barId;
    private int userId;
    private double tip; // Renamed field
    private List<DrinkOrder> drinks;
    private boolean inAppPayments;
    private boolean happyHour;
    @Nullable
    private String status;
    @Nullable
    private String claimer;

    @Override
    public String toString() {
        return "OrderRequest{" +
                "barId=" + barId +
                ", userId=" + userId +
                ", tip=" + tip + // Updated toString
                ", drinks=" + drinks +
                ", inAppPayments=" + inAppPayments +
                ", happyHour=" + happyHour +
                ", status=" + status +
                ", claimer=" + claimer +
                '}';
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