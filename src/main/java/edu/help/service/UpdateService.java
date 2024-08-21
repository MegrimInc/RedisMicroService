package edu.help.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.OrderResponse.DrinkOrder;

@Service
public class UpdateService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateService(RedisTemplate<String, Object> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    public List<String> getKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();

        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).build();
            Cursor<byte[]> cursor = connection.scan(options);

            while (cursor.hasNext()) {
                keys.add(new String(cursor.next())); // Convert byte[] to String
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return keys;
    }

    public void delete(String key) {
        if (redisTemplate.hasKey(key)) {
            redisTemplate.delete(key);
            System.out.println("Deleted key from Redis: " + key);
        } else {
            System.out.println("Key does not exist in Redis: " + key);
        }
    }

    
    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for barId: " + orderRequest.getBarId());
        System.out.println("Order Request sent to PostgreSQL: " + orderRequest);

        // Check if the key already exists in Redis
        String orderKey = generateOrderKey(orderRequest);
        if (redisTemplate.hasKey(orderKey)) {
            System.out.println("This is the orderKey: " + orderKey);
            sendResponse(session, "Order already in progress for barId: " + orderRequest.getBarId());
            System.out.println("Attempting to close WebSocket session for orderKey: " + orderKey);

            try {
                session.close();
                System.out.println("Closing WebSocket session for orderKey: ");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        
        
        // Try to process the order with PostgreSQL
        try {
            OrderResponse orderResponse = restTemplate.postForObject(
                    "http://34.230.32.169:8080/" + orderRequest.getBarId() + "/processOrder",
                    orderRequest,
                    OrderResponse.class
            );
            System.out.println("Received OrderResponse: " + orderResponse);

            if (orderResponse != null) {
                if ("Bar is closed".equals(orderResponse.getMessage())) {
                    // If the bar is closed, send a message back to the client and return
                    sendResponse(session, "Bar is closed. Please try again later.");
                    session.close();
                    return;
                }

                // Prepare the data to store in Redis
                Map<String, Object> orderData = new HashMap<>();
                orderData.put("status", "unready");
                orderData.put("user_id", orderRequest.getUserId());

                // Formatting the drinks as a map of drinkId -> drinkName,quantity
                Map<String, String> drinksMap = new HashMap<>();
                for (DrinkOrder drink : orderResponse.getDrinks()) {
                    drinksMap.put(String.valueOf(drink.getDrinkId()), drink.getDrinkName() + "," + drink.getQuantity());
                }
                orderData.put("drinks", drinksMap);

                orderData.put("total_price", orderResponse.getTotalPrice());
                orderData.put("timestamp", getCurrentTimestamp());

                // Store the order in Redis
                redisTemplate.opsForHash().putAll(orderKey, orderData);
                System.out.println("Stored order in Redis with key: " + orderKey);

                // Send a response back to the client
                sendResponse(session, "Order processed: " + orderResponse.getMessage());
            } else {
                sendResponse(session, "Failed to process order: No response from PostgreSQL.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(session, "Failed to process order: " + e.getMessage());
        }
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendResponse(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    public void set(String key, String jsonValue) {
        redisTemplate.opsForValue().set(key, jsonValue);
    }

    public String get(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void storeSession(String barId, String bartenderID, WebSocketSession session) {
        try {
            Map<String, Object> valueMap = new HashMap<>();
            valueMap.put("sessionId", session.getId()); // Store session ID or other relevant info
            valueMap.put("active", true); // Boolean value

            String redisKey = barId + "." + bartenderID;
            String redisValueJson = objectMapper.writeValueAsString(valueMap);

            set(redisKey, redisValueJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
