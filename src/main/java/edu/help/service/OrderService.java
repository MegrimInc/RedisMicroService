package edu.help.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;


import edu.help.dto.Order;
import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.ResponseWrapper;
import edu.help.websocket.BartenderWebSocketHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.json.commands.RedisJsonV1Commands;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
 
    private final JedisPooled jedisPooled; // Redis client for simple operations
    private final JedisPool jedisPool; // Redis connection pool for transactions
    

    public OrderService(RestTemplate restTemplate, JedisPooled jedisPooled, JedisPool jedisPool) {
        this.restTemplate = restTemplate;
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for barId: " + orderRequest.getBarId());
    
        // Fetch open and happyHour status from Redis
        String barKey = String.valueOf(orderRequest.getBarId());
        Boolean isOpen = Boolean.valueOf(jedisPooled.hget(barKey, "open"));
        Boolean isHappyHour = Boolean.valueOf(jedisPooled.hget(barKey, "happyHour"));
    
        // Check if the bar is open
        if (isOpen == null || !isOpen) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,  // No data, as the bar is closed
                "Failed to process order: The bar is currently closed."
            ));
            return;
        }
    
        String orderKey = generateOrderKey(orderRequest);
        System.out.println("Generated order key: " + orderKey);
        
        if (jedisPooled.exists(orderKey)) {
            System.out.println("Order key exists in Redis: " + orderKey);
            
            // Retrieve the JSON object as a raw object
            Object orderJsonObj = jedisPooled.jsonGet(orderKey);
            System.out.println("Raw JSON object retrieved: " + orderJsonObj);
        
            if (orderJsonObj != null) {
                // Convert the JSON object to a string
                String orderJson;
                try {
                    orderJson = objectMapper.writeValueAsString(orderJsonObj);
                    System.out.println("Order JSON string: " + orderJson);
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to serialize existing order data: " + e.getMessage());
                    sendOrderResponse(session, new ResponseWrapper(
                        "error",
                        null,
                        "Failed to process existing order data."
                    ));
                    return;
                }
        
                // Deserialize the string back into an Order object
                Order existingOrder;
                try {
                    existingOrder = objectMapper.readValue(orderJson, Order.class);
                    System.out.println("Deserialized Order object: " + existingOrder);
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to deserialize existing order data: " + e.getMessage());
                    sendOrderResponse(session, new ResponseWrapper(
                        "error",
                        null,
                        "Failed to process existing order data."
                    ));
                    return;
                }
        
                // Check the status of the existing order
                String existingStatus = existingOrder.getStatus();
                System.out.println("Existing order status: " + existingStatus);
        
                if (!"delivered".equals(existingStatus) && !"canceled".equals(existingStatus)) {
                    System.out.println("Order already in progress, status: " + existingStatus);
                    sendOrderResponse(session, new ResponseWrapper(
                        "error",
                        null,
                        "Order already in progress for barId: " + orderRequest.getBarId()
                    ));
                    return;
                }
            }
        }
        
        System.out.println("No existing order in progress or status is 'delivered' or 'canceled'. Proceeding with order processing.");
    
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
    
                jedisPooled.jsonSetWithEscape(orderKey, order);
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

   
    public void cancelOrderIfUnclaimed(int barId, int userId, WebSocketSession session) {
    String key = String.format("%d.%d", barId, userId);
    System.out.println("Attempting to cancel order for key: " + key);

    try (Jedis jedis = jedisPool.getResource()) {
        System.out.println("Obtained Jedis instance from the pool.");
        jedis.watch(key);
        System.out.println("Watching key: " + key);

        // Retrieve the JSON object as a raw object
        Object orderJsonObj = jedisPooled.jsonGet(key);

        if (orderJsonObj == null) {
            System.out.println("Key does not exist, unwatching.");
            jedis.unwatch();
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Order does not exist."
            ));
            return;
        }

        // Convert the JSON object to a string
        String orderJson;
        try {
            orderJson = objectMapper.writeValueAsString(orderJsonObj);
        } catch (JsonProcessingException e) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to process order data."
            ));
            jedis.unwatch(); // Unwatch if processing fails
            return;
        }

        // Deserialize the string back into an Order object
        Order order;
        try {
            order = objectMapper.readValue(orderJson, Order.class);
        } catch (JsonProcessingException e) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to process order data."
            ));
            jedis.unwatch(); // Unwatch if processing fails
            return;
        }

        // Check the claimer field in the Order object
        String claimer = order.getClaimer();
        System.out.println("Claimer field value: " + claimer);

        if (!claimer.isEmpty()) {
            System.out.println("Order has a claimer; no action taken. Unwatching key.");
            jedis.unwatch();
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Order can no longer be canceled."
            ));
            return;
        }

        System.out.println("Claimer is empty, proceeding with transaction.");
        Transaction transaction = jedis.multi();
        System.out.println("Transaction started.");

        // Update the status to "canceled"
        order.setStatus("canceled");
        order.setClaimer(String.valueOf(userId));
        System.out.println("Order status set to canceled.");

        // Serialize the updated order back to JSON
        String updatedOrderJson;
        try {
            updatedOrderJson = objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to serialize updated order data."
            ));
            jedis.unwatch(); // Unwatch if processing fails
            return;
        }

        // Set the updated JSON string back into Redis
        transaction.jsonSet(key, updatedOrderJson);
        System.out.println("Updated Order set back to Redis.");

        List<Object> results = transaction.exec();
        System.out.println("Transaction executed. Results: " + results);

        if (results == null || results.isEmpty()) {
            System.out.println("Failed to update the order status due to a conflict. Please try again.");
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to cancel the order due to a conflict. Please try again."
            ));
            return;
        }

        System.out.println("Order status updated to 'canceled'.");
        sendOrderResponse(session, new ResponseWrapper(
            "delete",
            null,
            "Order canceled successfully."
        ));
    } catch (Exception e) {
        System.err.println("Error processing order: " + e.getMessage());
        e.printStackTrace();
        sendOrderResponse(session, new ResponseWrapper(
            "error",
            null,
            "Error occurred while processing the order."
        ));
    }
}





    
    public void refreshOrdersForUser(int userId, WebSocketSession session) {
        ScanParams scanParams = new ScanParams().match("*." + userId);
        String cursor = "0";
        List<Order> orders = new ArrayList<>();

        try {
            do {
                ScanResult<String> scanResult = jedisPooled.scan(cursor, scanParams);
                cursor = scanResult.getCursor();

                for (String key : scanResult.getResult()) {
                    String type = jedisPooled.type(key);
                    System.out.println("Data type of key: " + type);

                    if ("ReJSON-RL".equals(type)) {
                        Object jsonObject = jedisPooled.jsonGet(key);

                        System.out.println("Raw JSON from Redis: " + jsonObject);

                        if (jsonObject != null) {
                            Map<String, Object> orderMap = objectMapper.convertValue(jsonObject,
                                    new TypeReference<Map<String, Object>>() {
                                    });

                            orderMap.put("sessionId", session.getId());

                            // Convert the modified Map back to a JSON string
                            String updatedJsonString = objectMapper.writeValueAsString(orderMap);

                            // Store the updated JSON string back in Redis under the same key
                            jedisPooled.jsonSet(key, updatedJsonString);

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