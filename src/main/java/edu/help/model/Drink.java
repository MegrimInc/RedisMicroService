package edu.help.model;

public class Drink {

    private int drinkId;
    private String drinkName;
    private int drinkQuantity;

    public Drink(int drinkId, String drinkName, int drinkQuantity) {
        this.drinkId = drinkId;
        this.drinkName = drinkName;
        this.drinkQuantity = drinkQuantity;
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

    public int getDrinkQuantity() {
        return drinkQuantity;
    }

    public void setDrinkQuantity(int drinkQuantity) {
        this.drinkQuantity = drinkQuantity;
    }
}
