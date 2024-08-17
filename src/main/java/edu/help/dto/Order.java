package edu.help.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class Order {

    @JsonProperty("barId")
    private int barId;

    @JsonProperty("orderId")
    private int orderId;

    @JsonProperty("userId")
    private int userId;

    @JsonProperty("price")
    private double price;

    @JsonProperty("name")
    private List<String> name;

    @JsonProperty("orderState")
    private String orderState;

    @JsonProperty("claimer")
    private String claimer;

    @JsonProperty("timestamp")
    private int timestamp; // Milliseconds since epoch

    // Default constructor needed for Jackson
    public Order() {}

    public Order(int barId, int orderId, int userId, double price, List<String> name, String orderState, String claimer, int timestamp) {
        this.barId = barId;
        this.orderId = orderId;
        this.userId = userId;
        this.price = price;
        this.name = name;
        this.orderState = orderState;
        this.claimer = claimer;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public int getBarId() {
        return barId;
    }

    public void setBarId(int barId) {
        this.barId = barId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public List<String> getName() {
        return name;
    }

    public void setName(List<String> name) {
        this.name = name;
    }

    public String getOrderState() {
        return orderState;
    }

    public void setOrderState(String orderState) {
        this.orderState = orderState;
    }

    public String getClaimer() {
        return claimer;
    }

    public void setClaimer(String claimer) {
        this.claimer = claimer;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    // Method to convert an Order to JSON
    public String toJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }

    // Static method to create an Order from JSON
    public static Order fromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Order.class);
    }

    // Method to get the age of the order in seconds
    public int getAge() {
        long currentTime = System.currentTimeMillis();
        return (int) ((currentTime - this.timestamp) / 1000);
    }
}
