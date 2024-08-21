package edu.help.websocket;
//TODO: add code for detecting and redistributing severed connections.

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.help.dto.Order;
import edu.help.dto.OrderResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.service.UpdateService;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {

    private final UpdateService redisService2;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(); // Session storage
    private final RestTemplate restTemplate;

    public BartenderWebSocketHandler(UpdateService redisService2, RestTemplate restTemplate) {
        this.redisService2 = redisService2;
        this.restTemplate = restTemplate;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Log the received message payload
        System.out.println("Bartender WebSocket message received: " + message.getPayload());

        // Parse the JSON message
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

        String action = (String) payload.get("action");
        switch (action) {
            case "initialize":
                int barID = (int) payload.get("barID");
                String bartenderID = (String) payload.get("bartenderID");

                if (bartenderID == null || bartenderID.isEmpty() || !bartenderID.matches("[a-zA-Z]+")) {
                    // Send an error response
                    sendErrorMessage(session, "Invalid bartenderID. It must be non-empty and contain only alphabetic characters.");
                    return;
                }

                // Create the Redis key
                String redisKey = barID + "." + bartenderID;

                // Check if there's an existing session for this bartender and close it
                String existingSessionJson = redisService2.get(redisKey);
                if (existingSessionJson != null) {
                    // Parse the existing session data
                    Map<String, Object> existingSessionData = objectMapper.readValue(existingSessionJson, Map.class);
                    String existingSessionId = (String) existingSessionData.get("sessionId");
                    WebSocketSession existingSession = sessionMap.get(existingSessionId);
                    if (existingSession != null && existingSession.isOpen()) {
                        existingSession.close();
                    }
                }

                // Store the new session in Redis and in the session map
                Map<String, Object> redisValue = new HashMap<>();
                redisValue.put("sessionId", session.getId()); // Store session ID or other relevant info
                redisValue.put("active", true); // Boolean value

                String redisValueJson = objectMapper.writeValueAsString(redisValue);
                redisService2.set(redisKey, redisValueJson);

                sessionMap.put(session.getId(), session); // Store the session in the session map

                session.sendMessage(new TextMessage("Initialization successful for bartender " + bartenderID));
                break;

            case "refresh":
                int barID2 = (int) payload.get("barID");

                List<Order> orders = getOrdersFromDataSource(barID2);

                // Convert the list of orders to JSON and send it to the client
                String ordersJson = objectMapper.writeValueAsString(orders);
                session.sendMessage(new TextMessage(ordersJson));
                break;

            case "claim":
                int barIDClaim = (int) payload.get("barID");
                int orderID = (int) payload.get("orderID");
                String claimingBartenderID = (String) payload.get("bartenderID");

                // Construct the Redis key for the order
                String orderRedisKey = barIDClaim + "." + orderID;

                // Retrieve the order from Redis
                String orderJson = redisService2.get(orderRedisKey);

                if (orderJson == null) {
                    // If order doesn't exist in Redis, send a failure response
                    sendErrorMessage(session, "Order does not exist.");
                    break;
                }

                // Parse the order JSON to a Map
                Map<String, Object> orderData = objectMapper.readValue(orderJson, Map.class);
                String currentClaimer = (String) orderData.get("claimer");

                if (currentClaimer != null && !currentClaimer.isEmpty()) {
                    // If the order is already claimed, send a failure response
                    sendErrorMessage(session, "Order already claimed by " + currentClaimer + ".");
                    break;
                }

                // Update the order with the claiming bartender's ID
                orderData.put("claimer", claimingBartenderID);

                // Convert the updated order data back to JSON and store it in Redis
                String updatedOrderJson = objectMapper.writeValueAsString(orderData);
                redisService2.set(orderRedisKey, updatedOrderJson);

                // Retrieve all the bartenders of the current bar to notify them
                List<String> bartenderKeys = redisService2.getKeys(barIDClaim + ".*");

// Filter keys to only include those that match the pattern barID.ALPHA_STRING
                List<String> filteredBartenderKeys = bartenderKeys.stream()
                        .filter(key -> key.matches(barIDClaim + "\\.[a-zA-Z]+"))
                        .collect(Collectors.toList());
                for (String bartenderKey : bartenderKeys) {
                    // Skip if it's the same bartender who claimed the order
                    if (bartenderKey.endsWith(claimingBartenderID)) {
                        continue;
                    }

                    String bartenderSessionJson = redisService2.get(bartenderKey);
                    Map<String, Object> bartenderData = objectMapper.readValue(bartenderSessionJson, Map.class);
                    String bartenderSessionId = (String) bartenderData.get("sessionId");

                    WebSocketSession bartenderSession = sessionMap.get(bartenderSessionId);
                    if (bartenderSession != null && bartenderSession.isOpen()) {
                        // Send the updated order to the other bartenders
                        Map<String, Object> responsePayload = new HashMap<>();
                        responsePayload.put("orders", List.of(orderData));  // Wrap the order data in a list

                        String responseJson = objectMapper.writeValueAsString(responsePayload);
                        bartenderSession.sendMessage(new TextMessage(responseJson));
                    }
                }

                // Also send the updated order to the current bartender as part of the UI update
                Map<String, Object> responsePayload = new HashMap<>();
                responsePayload.put("orders", List.of(orderData));  // Wrap the order data in a list

                String responseJson = objectMapper.writeValueAsString(responsePayload);
                session.sendMessage(new TextMessage(responseJson));

                break;

            case "unclaim":
                break;

            case "ready":
                break;


            case "deliver":
                int barID3 = (int) payload.get("barID");
                int orderID3 = (int) payload.get("orderID");
                finalizeOrder("deliver", barID3, orderID3);

            case "cancel":
                int barID4 = (int) payload.get("barID");
                int orderID4 = (int) payload.get("orderID");
                finalizeOrder("cancel", barID4, orderID4);
                break;

            case "disable":
                break;

            case "open":
                break;

            case "close":
                break;

            default:
                sendErrorMessage(session, "Unknown action: " + action);
                break;
        }
    }


    private void finalizeOrder(String action, int barID, int orderID)
    {
        String orderRedisKey = barID + "." + orderID;
        String orderJson = redisService2.get(orderRedisKey);

        if (orderJson == null) {
            System.err.println("Order does not exist in Redis, cannot " + action);
            return;
        }

        try {
            Order order = objectMapper.readValue(orderJson, Order.class);

            // Create a payload to send to PostgreSQL, including the action
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("order", order);
            requestPayload.put("action", action);

            // Send the order and action to PostgreSQL-based service using RestTemplate
            try {
                OrderResponse orderResponse = restTemplate.postForObject(
                    "http://34.230.32.169:8080/processFinalizedOrder",
                    requestPayload,
                    OrderResponse.class
                );

                if (orderResponse != null && "Order processed successfully".equals(orderResponse.getMessage())) {
                    // Remove the order from Redis
                    redisService2.delete(orderRedisKey);
                    System.out.println("Order marked as " + action + " and stored in PostgreSQL.");
                } else {
                    System.err.println("Failed to process the order in PostgreSQL: " + (orderResponse != null ? orderResponse.getMessage() : "No response"));
                }
            } catch (Exception e) {
                System.err.println("Error sending order to PostgreSQL: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
    private List<Order> getOrdersFromDataSource(int barID) {
        // Retrieve all keys matching the pattern BARID.*
        List<String> allKeys = redisService2.getKeys(barID + ".*");

        // Filter and collect orders where keys match BARID.INTEGER

        return allKeys.stream()
                .filter(key -> key.matches(barID + "\\.\\d+"))
                .map(key -> {
                    String jsonOrder = redisService2.get(key); // Retrieve JSON for each order
                    try {
                        return objectMapper.readValue(jsonOrder, Order.class); // Convert JSON to Order object
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws JsonProcessingException {
        Map<String, String> errorPayload = new HashMap<>();
        errorPayload.put("error", errorMessage);
        String errorJson = objectMapper.writeValueAsString(errorPayload);
        try {
            session.sendMessage(new TextMessage(errorJson));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
