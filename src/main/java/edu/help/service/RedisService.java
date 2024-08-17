package edu.help.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.OrderResponse.DrinkOrder;

@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public RedisService(RedisTemplate<String, Object> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
       
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for barId: " + orderRequest.getBarId());
        System.out.println("Redis expects: " + Arrays.toString(OrderResponse.class.getDeclaredFields()));

    
        // Check if the key already exists in Redis
        String orderKey = generateOrderKey(orderRequest);
        if (redisTemplate.hasKey(orderKey)) {
            System.out.println("This is the orderKey: " + orderKey);
            sendResponse(session, new OrderResponse(
            "Order already in progress for barId: " + orderRequest.getBarId(),
            0.0,
            null,
            "" // Corrected messageType
        ));
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
                    sendResponse(session, new OrderResponse(
                        "Bar is closed. Please try again later.",
                        0.0,
                        null,
                        "" // Using "update" for bar closure
                    ));
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
    
                sendResponse(session, new OrderResponse(
                    "Order processed: " + orderResponse.getMessage(),
                    orderResponse.getTotalPrice(),
                    orderResponse.getDrinks(),
                    "success" // Using "success" to indicate successful processing
                ));
            } 
        } catch (RestClientException e) {
            e.printStackTrace();
            try {
                // Send error response to the client
                sendResponse(session, new OrderResponse(
                    "Failed to process order: No response from PostgreSQL.",
                    0.0,
                    null,
                    "error" // Using "error" to indicate a problem with processing
                ));
                
                // Close the WebSocket session
                if (session.isOpen()) {
                    session.close();
                    System.out.println("WebSocket session closed due to error.");
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
    

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendResponse(WebSocketSession session, OrderResponse orderResponse) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(orderResponse);
            session.sendMessage(new TextMessage(jsonResponse));
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
