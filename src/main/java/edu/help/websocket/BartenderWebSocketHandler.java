package edu.help.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import edu.help.service.RedisService;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {
    private final RedisService redisService;

@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    // Log the received message payload
    System.out.println("Bartender WebSocket message received: " + message.getPayload());

    // Parse the JSON message
    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

    String action = (String) payload.get("action");
    if ("initialize".equals(action)) {
        int barID = (int) payload.get("barID");
        String bartenderID = (String) payload.get("bartenderID");

        if (bartenderID == null || bartenderID.isEmpty() || !bartenderID.matches("[a-zA-Z]+")) {
            // Send an error response
            session.sendMessage(new TextMessage("Error: Invalid bartenderID. It must be non-empty and contain only alphabetic characters."));
            return;
        }
        
        // Create the Redis key
        String redisKey = barID + "." + bartenderID;

        // Create the Redis value object
        Map<String, Object> redisValue = new HashMap<>();
        redisValue.put("sessionId", session.getId()); // Store the session ID or other identifier
        redisValue.put("active", true); // Boolean value

        // Serialize the Redis value object to JSON
        String redisValueJson = objectMapper.writeValueAsString(redisValue);

        // Store the value in Redis
        redisService.set(redisKey, redisValueJson);

        session.sendMessage(new TextMessage("Initialization successful for bartender " + bartenderID));
    } else {
        session.sendMessage(new TextMessage("Unknown action: " + action));
    }
}

}
