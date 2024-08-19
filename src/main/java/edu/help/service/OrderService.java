package edu.help.service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.Order;
import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.ResponseWrapper;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPooled jedis; // Redis client

    public OrderService(RestTemplate restTemplate, LettuceConnectionFactory redisConnectionFactory) {
        this.restTemplate = restTemplate;
        this.jedis = new JedisPooled(redisConnectionFactory.getHostName(), redisConnectionFactory.getPort());
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for barId: " + orderRequest.getBarId());

        // Fetch open and happyHour status from Redis
        String barKey = "bar:" + orderRequest.getBarId();
        Boolean isOpen = Boolean.parseBoolean(jedis.hget(barKey, "open"));
        Boolean isHappyHour = Boolean.parseBoolean(jedis.hget(barKey, "happyHour"));

        // Check if the bar is open
        if (isOpen == null || !isOpen) {
            System.err.println("Bar is closed, order cannot be processed for barId: " + orderRequest.getBarId());
            sendErrorResponse(session, "Failed to process order: The bar is currently closed.");
            return;
        }

        // Set the happy hour status in the OrderRequest
        if (isHappyHour != null) {
            orderRequest.setIsHappyHour(isHappyHour);
        } else {
            System.err.println("Happy Hour status not found in Redis for barId: " + orderRequest.getBarId());
            sendErrorResponse(session, "Failed to retrieve happy hour status.");
            return;
        }

        // Check if the key already exists in Redis (for duplicate order prevention)
        String orderKey = generateOrderKey(orderRequest);
        if (jedis.exists(orderKey)) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Order already in progress for barId: " + orderRequest.getBarId()
            ));
            closeSession(session);
            return;
        }

        try {
            // Send order request to PostgreSQL (already containing the happy hour status)
            OrderResponse orderResponse = restTemplate.postForObject(
                "http://34.230.32.169:8080/" + orderRequest.getBarId() + "/processOrder",
                orderRequest,
                OrderResponse.class
            );

            if (orderResponse != null) {
                // Create Order object and store it in Redis
                Order order = new Order(
                    orderRequest.getBarId(),
                    orderRequest.getUserId(),
                    orderResponse.getTotalPrice(),
                    convertDrinksToOrders(orderResponse.getDrinks()),
                    "unready",
                    "",
                    getCurrentTimestamp()
                );

                jedis.jsonSetWithEscape(orderKey, order);
                System.out.println("Stored order in Redis with key: " + orderKey);

                sendOrder(order, session);
            }
        } catch (RestClientException e) {
            closeSession(session);
            e.printStackTrace();
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to process order: No response from PostgreSQL."
            ));
        }
    }

    private List<Order.DrinkOrder> convertDrinksToOrders(List<OrderResponse.DrinkOrder> drinkResponses) {
        return drinkResponses.stream()
                             .map(drink -> new Order.DrinkOrder(drink.getDrinkId(), drink.getDrinkName(), drink.getQuantity()))
                             .toList();
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendOrderResponse(WebSocketSession session, ResponseWrapper responseWrapper) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(responseWrapper);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendOrder(Order order, WebSocketSession session) {
        try {
            ResponseWrapper responseWrapper = new ResponseWrapper(
                "success",
                order,
                "Order processed successfully."
            );
            String jsonResponse = objectMapper.writeValueAsString(responseWrapper);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendErrorResponse(WebSocketSession session, String message) {
        try {
            ResponseWrapper responseWrapper = new ResponseWrapper(
                "error",
                null,
                message
            );
            sendOrderResponse(session, responseWrapper);
            if (session is open()) {
                session.close();
                System.out.println("WebSocket session closed due to error.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
                System.out.println("WebSocket session closed.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(formatter);
    }

    public boolean deleteOrderIfExists(int barId, int userId) {
        String key = String.format("%d.%d", barId, userId);
        
        System.out.println("Checking existence of key: " + key);
        
        if (jedis.exists(key)) {
            System.out.println("Key exists. Retrieving JSON data...");
            
            try {
                Object JsonObject = jedis.jsonGet(key);
                
                System.out.println("Raw JSON String: " + JsonObject);
                
                if (JsonObject != null) {
                    String jsonString = objectMapper.writeValueAsString(JsonObject);
                    JsonNode jsonNode = objectMapper.readTree(jsonString);
                    
                    String claimer = jsonNode.path("claimer").asText("");
                    
                    System.out.println("Claimer field value: " + claimer);
                    
                    if (claimer.isEmpty()) {
                        System.out.println("Claimer is empty. Deleting key: " + key);
                        jedis.del(key);
                        return true;
                    } else {
                        System.out.println("Claimer is not empty. No action taken.");
                    }
                } else {
                    System.out.println("JSON String is null or empty.");
                }
            } catch (JsonProcessingException e) {
                System.err.println("Error processing JSON data: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Key does not exist.");
        }
        
        return false;
    }
    
    public void refreshOrdersForUser(int userId, WebSocketSession session) {
        ScanParams scanParams = new ScanParams().match("*." + userId);
        String cursor = "0";
        List<Order> orders = new ArrayList<>();
        
        try {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                
                for (String key : scanResult.getResult()) {
                    String type = jedis.type(key);
                    System.out.println("Data type of key: " + type);
                    
                    if ("ReJSON-RL".equals(type)) {
                        Object jsonObject = jedis.jsonGet(key);
                        
                        System.out.println("Raw JSON from Redis: " + jsonObject);
                        
                        if (jsonObject != null) {
                            String jsonString = objectMapper.writeValueAsString(jsonObject);
                            
                            System.out.println("Converted JSON String: " + jsonString);

                            Order order = objectMapper.readValue(jsonString, Order.class);
                            orders.add(order);
                        }
                    } else {
                        System.err.println("Skipping key with unsupported type: " + key);
                    }
                }
            } while (!"0".equals(cursor));
            
            if (orders.isEmpty()) {
                sendOrderResponse(session, new ResponseWrapper(
                    "info",
                    null,
                    "No orders found for the user."
                ));
            } else {
                for (Order order : orders) {
                    sendOrderResponse(session, new ResponseWrapper(
                        "success",
                        order,
                        "Order details retrieved successfully."
                    ));
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            sendErrorResponse(session, "Failed to retrieve orders.");
        }
    }
}
