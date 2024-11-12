package edu.help.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
//This is the same as https://github.com/BarzzyLLC/frontend/blob/0.0.0/lib/backend/customerorder2.dart
public class Order {
    private int barId;
    private int userId;
    private double totalRegularPrice;
    private double tip; // Renamed field
    private boolean inAppPayments;
    private List<DrinkOrder> drinks;
    private String status;
    private String claimer;
    private String timestamp;
    private String sessionId;

    public Order(int barId, int userId, double totalRegularPrice, double tip, boolean inAppPayments,
                 List<DrinkOrder> drinks, String status, String claimer, String timestamp, String sessionId) {
        this.barId = barId;
        this.userId = userId;
        this.totalRegularPrice = totalRegularPrice;
        this.tip = tip; // Updated initialization
        this.inAppPayments = inAppPayments;
        this.drinks = drinks;
        this.status = status;
        this.claimer = claimer;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }

    public Order() {
    }

    @Override
    public String toString() {
        return "Order{" +
                "barId=" + barId +
                ", userId=" + userId +
                ", totalRegularPrice=" + totalRegularPrice +
                ", tip=" + tip + // Updated toString
                ", inAppPayments=" + inAppPayments +
                ", drinks=" + drinks +
                ", status='" + status + '\'' +
                ", claimer='" + claimer + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }




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

    @JsonProperty("totalRegularPrice")
    public double getTotalRegularPrice() {
        return totalRegularPrice;
    }

    @JsonProperty("totalRegularPrice")
    public void setTotalRegularPrice(double totalRegularPrice) {
        this.totalRegularPrice = totalRegularPrice;
    }

    @JsonProperty("tip")
    public double getTip() {
        return tip;
    }

    @JsonProperty("tip")
    public void setTip(double tip) {
        this.tip = tip;
    }


    @JsonProperty("inAppPayments")
    public boolean isInAppPayments() {
        return inAppPayments;
    }

    @JsonProperty("inAppPayments")
    public void setInAppPayments(boolean inAppPayments) {
        this.inAppPayments = inAppPayments;
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

    @JsonProperty("sessionId")
    public String getSessionId() {
        return sessionId;
    }

    @JsonProperty("sessionId")
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    // Updated DrinkOrder class with drinkName
    public static class DrinkOrder {
        private int drinkId;
        private String drinkName;  // Added back drinkName
        private String paymentType;  // "points" or "regular"
        private String sizeType;     // "single" or "double"
        private int quantity;

        public DrinkOrder(int drinkId, String drinkName, String paymentType, String sizeType, int quantity) {
            this.drinkId = drinkId;
            this.drinkName = drinkName;
            this.paymentType = paymentType;
            this.sizeType = sizeType;
            this.quantity = quantity;
        }

        public DrinkOrder() {
        }

        @Override
        public String toString() {
            return "DrinkOrder{" +
                    "drinkId=" + drinkId +
                    ", drinkName='" + drinkName + '\'' +
                    ", paymentType='" + paymentType + '\'' +
                    ", sizeType='" + sizeType + '\'' +
                    ", quantity=" + quantity +
                    '}';
        }

        @JsonProperty("drinkId")
        public int getDrinkId() {
            return drinkId;
        }

        @JsonProperty("drinkId")
        public void setDrinkId(int drinkId) {
            this.drinkId = drinkId;
        }

        @JsonProperty("drinkName")
        public String getDrinkName() {
            return drinkName;
        }

        @JsonProperty("drinkName")
        public void setDrinkName(String drinkName) {
            this.drinkName = drinkName;
        }

        @JsonProperty("paymentType")
        public String getPaymentType() {
            return paymentType;
        }

        @JsonProperty("paymentType")
        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
        }

        @JsonProperty("sizeType")
        public String getSizeType() {
            return sizeType;
        }

        @JsonProperty("sizeType")
        public void setSizeType(String sizeType) {
            this.sizeType = sizeType;
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