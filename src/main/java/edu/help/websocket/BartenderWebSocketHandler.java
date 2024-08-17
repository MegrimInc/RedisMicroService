package edu.help.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.help.model.Order;
import org.springframework.stereotype.Component;
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

    public BartenderWebSocketHandler(UpdateService redisService2) {
        this.redisService2 = redisService2;
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
                    session.sendMessage(new TextMessage("Error: Invalid bartenderID. It must be non-empty and contain only alphabetic characters."));
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

            default:
                session.sendMessage(new TextMessage("Unknown action: " + action));
                break;
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

}
