package edu.help.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
//This is the same as https://github.com/MegrimLLC/frontend/blob/0.0.0/lib/backend/customerorder2.dart
public class Order {
    private String sessionId, name, status, terminal, timestamp ; //TODO WESLEY change to string and refactor
    private int merchantId, customerId, totalPointPrice;
    private double totalRegularPrice, totalGratuity, totalServiceFee, totalTax;
    private boolean inAppPayments, pointOfSale;
    private List<ItemOrder> items;

    public Order(String name, int merchantId, int customerId, double totalRegularPrice, int totalPointPrice, double totalGratuity, double totalServiceFee, double totalTax, boolean inAppPayments,
            List<ItemOrder> items, boolean pointOfSale, String status, String terminal, String timestamp, String sessionId) {
        this.name = name;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.totalRegularPrice = totalRegularPrice;
        this.totalPointPrice = totalPointPrice; 
        this.totalGratuity = totalGratuity;
        this.totalGratuity = totalGratuity;
        this.totalServiceFee = totalServiceFee;
        this.totalTax = totalTax; 
        this.inAppPayments = inAppPayments;
        this.items = items;
        this.pointOfSale = pointOfSale;
        this.status = status;
        this.terminal = terminal;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }

    public Order() {
    }

    @Override
    public String toString() {
        return "Order{" +
                "name=" + name +
                "merchantId=" + merchantId +
                ", customerId=" + customerId +
                ", totalRegularPrice=" + totalRegularPrice +
                ", totalPointPrice=" + totalPointPrice +
                ", totalGratuity=" + totalGratuity +
                ", totalServiceFee=" + totalServiceFee +
                ", totalTax=" + totalTax + 
                ", inAppPayments=" + inAppPayments +
                ", items=" + items +
                ", status='" + status + '\'' +
                ", terminal='" + terminal + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }


    @JsonProperty("merchantId")
    public int getMerchantId() {
        return merchantId;
    }

    @JsonProperty("merchantId")
    public void setMerchantId(int merchantId) {
        this.merchantId = merchantId;
    }

    @JsonProperty("customerId")
    public int getCustomerId() {
        return customerId;
    }

    @JsonProperty("customerId")
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    @JsonProperty("totalRegularPrice")
    public double getTotalRegularPrice() {
        return totalRegularPrice;
    }

    @JsonProperty("totalRegularPrice")
    public void setTotalRegularPrice(double totalRegularPrice) {
        this.totalRegularPrice = totalRegularPrice;
    }

    @JsonProperty("totalGratuity")
    public double getTotalGratuity() {
        return totalGratuity;
    }

    @JsonProperty("totalGratuity")
    public void setTotalGratuity(double totalGratuity) {
        this.totalGratuity = totalGratuity;
    }

    @JsonProperty("totalServiceFee")
    public double getTotalServiceFee() {
        return totalServiceFee;
    }

    @JsonProperty("totalServiceFee")
    public void setTotalServiceFee(double totalServiceFee) {
        this.totalServiceFee = totalServiceFee;
    }

    @JsonProperty("totalTax")
    public double getTotalTax() {
        return totalTax;
    }

    @JsonProperty("totalTax")
    public void setTotalTax(double totalTax) {
        this.totalTax = totalTax;
    }

    @JsonProperty("inAppPayments")
    public boolean isInAppPayments() {
        return inAppPayments;
    }

    @JsonProperty("inAppPayments")
    public void setInAppPayments(boolean inAppPayments) {
        this.inAppPayments = inAppPayments;
    }

    @JsonProperty("items")
    public List<ItemOrder> getItems() {
        return items;
    }

    @JsonProperty("items")
    public void setItems(List<ItemOrder> items) {
        this.items = items;
    }

    @JsonProperty("pointOfSale")
    public boolean isPointOfSale() {
        return pointOfSale;
    }

    @JsonProperty("pointOfSale")
    public void setPointOfSale(boolean pointOfSale) {
        this.pointOfSale = pointOfSale;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("terminal")
    public String getTerminal() {
        return terminal;
    }

    @JsonProperty("terminal")
    public void setTerminal(String terminal) {
        this.terminal = terminal;
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

    @JsonProperty("totalPointPrice")
    public int getTotalPointPrice() {
        return totalPointPrice;
    }

    @JsonProperty("totalPointPrice")
    public void setTotalPointPrice(int totalPointPrice) {
        this.totalPointPrice = totalPointPrice;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    // Updated DrinkOrder class with itemName
    public static class ItemOrder {
        private int itemId;
        private String itemName;  // Added back itemName
        private String paymentType;  // "points" or "regular"    
        private int quantity;

        public ItemOrder(int itemId, String itemName, String paymentType, int quantity) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.paymentType = paymentType;
            this.quantity = quantity;
        }

        public ItemOrder() {
        }

        @Override
        public String toString() {
            return "DrinkOrder{" +
                    "itemId=" + itemId +
                    ", itemName='" + itemName + '\'' +
                    ", paymentType='" + paymentType + '\'' +
                    ", quantity=" + quantity +
                    '}';
        }

        @JsonProperty("itemId")
        public int getDrinkId() {
            return itemId;
        }

        @JsonProperty("itemId")
        public void setDrinkId(int itemId) {
            this.itemId = itemId;
        }

        @JsonProperty("itemName")
        public String getDrinkName() {
            return itemName;
        }

        @JsonProperty("itemName")
        public void setDrinkName(String itemName) {
            this.itemName = itemName;
        }

        @JsonProperty("paymentType")
        public String getPaymentType() {
            return paymentType;
        }

        @JsonProperty("paymentType")
        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
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