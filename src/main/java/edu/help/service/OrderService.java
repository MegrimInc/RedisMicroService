package edu.help.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.help.dto.Order;
import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.ResponseWrapper;
import edu.help.websocket.BartenderWebSocketHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
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
        String barKey = String.valueOf(orderRequest.getBarId());
        Boolean isOpen = Boolean.valueOf(jedis.hget(barKey, "open"));
        Boolean isHappyHour = Boolean.valueOf(jedis.hget(barKey, "happyHour"));
    
        // Check if the bar is open
        if (isOpen == null || !isOpen) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,  // No data, as the bar is closed
                "Failed to process order: The bar is currently closed."
            ));
            return;
        }
    
        // Check if the key already exists in Redis (for duplicate order prevention)
        String orderKey = generateOrderKey(orderRequest);
        if (jedis.exists(orderKey)) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,  // No data, as the order is already in progress
                "Order already in progress for barId: " + orderRequest.getBarId()
            ));
            return;
        }
    
        // Set the happy hour status in the OrderRequest
        if (isHappyHour != null) {
            orderRequest.setIsHappyHour(isHappyHour);
        } else {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,  // No data, as happy hour status is missing
                "Failed to retrieve happy hour status."
            ));
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
                    getCurrentTimestamp(),
                    session.getId() 
                );
    
                jedis.jsonSetWithEscape(orderKey, order);
                System.out.println("Stored order in Redis with key: " + orderKey);
    
                sendOrderResponse(session, new ResponseWrapper(
                    "create",
                    order,  // Send the actual order data
                    "Order successfully processed."
                ));
    
                // Initialize the data map for broadcasting
                Map<String, Object> data = new HashMap<>();
                data.put("orders", order);
    
                // Broadcast the new order to the bartenders
                try {
                    BartenderWebSocketHandler.getInstance().broadcastToBar(orderRequest.getBarId(), data);
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle the exception if necessary, or log the error
                }
            }
    
        } catch (RestClientException e) {
            e.printStackTrace();
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,  // No data, as an exception occurred
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


    private void sendErrorResponse(WebSocketSession session, String message) {
        try {
            ResponseWrapper responseWrapper = new ResponseWrapper(
                "error",
                null,
                message
            );
            sendOrderResponse(session, responseWrapper);
            if (session.isOpen()) {
                session.close();
                System.out.println("WebSocket session closed due to error.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
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
    

    // public boolean cancelOrderIfUnclaimed(int barId, int userId) {
    //     String key = String.format("%d.%d", barId, userId);
        
    //     try (Jedis jedis = jedisPool.getResource()) {
    //         jedis.watch(key); // Watch the key to avoid concurrent modifications
            
    //         Object jsonObject = jedis.jsonGet(key); // Retrieve the JSON object
            
    //         if (jsonObject == null) {
    //             System.out.println("Key does not exist.");
    //             jedis.unwatch(); // Unwatch if the key does not exist
    //             return false;
    //         }
            
    //         // Convert the retrieved JSON object to a JSON string
    //         String jsonString = objectMapper.writeValueAsString(jsonObject);
    //         JsonNode jsonNode = objectMapper.readTree(jsonString);
            
    //         String claimer = jsonNode.path("claimer").asText("");
            
    //         if (claimer.isEmpty()) {
    //             // Start a transaction
    //             Transaction transaction = jedis.multi();
                
    //             // Update the status to "canceled"
    //             JsonNode updatedJsonNode = jsonNode.deepCopy();
    //             ((ObjectNode) updatedJsonNode).put("status", "canceled");
    //             String updatedJsonString = objectMapper.writeValueAsString(updatedJsonNode);
                
    //             // Set the updated JSON back to Redis
    //             transaction.jsonSet(key, updatedJsonString);
                
    //             List<Object> results = transaction.exec(); // Execute the transaction
                
    //             if (results == null || results.isEmpty()) {
    //                 System.out.println("Failed to update the order status due to a conflict. Please try again.");
    //                 return false;
    //             }
                
    //             System.out.println("Order status updated to 'canceled'.");
    //             return true;
    //         } else {
    //             System.out.println("Order has a claimer; no action taken.");
    //             jedis.unwatch(); // Unwatch if the claimer is not empty
    //             return false;
    //         }
    //     } catch (Exception e) {
    //         System.err.println("Error processing order: " + e.getMessage());
    //         e.printStackTrace();
    //         return false;
    //     }
    // }







    
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
                            Map<String, Object> orderMap = objectMapper.convertValue(jsonObject,
                                    new TypeReference<Map<String, Object>>() {
                                    });

                            orderMap.put("sessionId", session.getId());

                            // Convert the modified Map back to a JSON string
                            String updatedJsonString = objectMapper.writeValueAsString(orderMap);

                            // Store the updated JSON string back in Redis under the same key
                            jedis.jsonSet(key, updatedJsonString);

                            System.out.println("Updated sessionId in Redis for key: " + key);

                            // Convert the updated JSON string to an Order object
                            Order order = objectMapper.readValue(updatedJsonString, Order.class);
                            // Add the order to the list of orders to be returned
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
                        "No orders found for the user."));
            } else {
                for (Order order : orders) {
                    sendOrderResponse(session, new ResponseWrapper(
                            "refresh",
                            order,
                            "Order details retrieved successfully."));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            sendErrorResponse(session, "Failed to retrieve orders.");
        }
    }
    
   
}