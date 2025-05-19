package edu.help.dto;

import jakarta.annotation.Nullable;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {
    private int merchantId;
    private int customerId;
    private List<ItemOrder> items;
    private boolean isDiscount;
    @Nullable
    private String terminal;

    @Override
    public String toString() {
        return "OrderRequest{" +
                "merchantId=" + merchantId +
                ", customerId=" + customerId +
                ", items=" + items +
                ", isDiscount=" + isDiscount +
                ", terminal=" + terminal +
                '}';
    }

    public static class ItemOrder {
        private int itemId;
        private int quantity;
        private String paymentType; // "points" or "regular"
       

        @Override
        public String toString() {
            return "ItemOrder{" +
                    "itemId=" + itemId +
                    ", quantity=" + quantity +
                    ", paymentType='" + paymentType + '\'' +
                    '}';
        }

        // Getters and Setters
        public int getItemId() {
            return itemId;
        }

        public void setItemId(int itemId) {
            this.itemId = itemId;
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
    }
}