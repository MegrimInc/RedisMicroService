package edu.help.dto;

import java.util.List;

public class OrderRequest {

    private int barId;
    private int userId;
    private Boolean isHappyHour;
    private List<DrinkOrder> drinks;

    @Override
public String toString() {
    return "OrderRequest{" +
            "barId=" + barId +
            ", userId=" + userId +
            ", happyHour=" + isHappyHour +
            ", drinks=" + drinks +
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

    public List<DrinkOrder> getDrinks() {
        return drinks;
    }

    public void setDrinks(List<DrinkOrder> drinks) {
        this.drinks = drinks;
    }

    public void setIsHappyHour(boolean isHappyHour)
    {
        this.isHappyHour = isHappyHour;
    }

    public boolean getIsHappyHour()
    {
        return isHappyHour;
    }

    public static class DrinkOrder {
        private int drinkId;
        private int quantity;

        @Override
        public String toString() {
            return "DrinkOrder{" +
                    "drinkId=" + drinkId +
                    ", quantity=" + quantity +
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
    }
}
