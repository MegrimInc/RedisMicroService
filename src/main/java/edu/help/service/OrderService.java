package edu.help.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import edu.help.websocket.StationWebSocketHandler;
import edu.help.websocket.OrderWebSocketHandler;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;


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
        System.out.println("Processing order for merchantId: " + orderRequest.getMerchantId());

        // Fetch the total quantity of items ordered
        int totalQuantity = orderRequest.getDrinks().stream()
                .mapToInt(OrderRequest.DrinkOrder::getQuantity)
                .sum();

        // Determine quantity limit based on payment type
        int quantityLimit = 10;

        // Check if total quantity exceeds the limit
        if (totalQuantity > quantityLimit) {
            String message = "You can only add up to 10 drinks per order";
     
            System.out.println("Order quantity limit exceeded: " + message);

            // Send error response and exit
            sendOrderResponse(session, new ResponseWrapper("error", null, message));
            return;
        }

        // Fetch open and happyHour status from Redis
        String merchantKey = String.valueOf(orderRequest.getMerchantId());
        Boolean isOpen = Boolean.valueOf(jedisPooled.hget(merchantKey, "open"));

        // Check if the merchant is open
        if (isOpen == null || !isOpen) {
            sendOrderResponse(session, new ResponseWrapper(
                    "error",
                    null, // No data, as the merchant is closed
                    "Failed to process order: The merchant is currently closed."));
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
                            "Failed to process existing order data."));
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
                            "Failed to process existing order data."));
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
                            "Order already in progress. Please cancel the current order before placing a new one."));
                    return;
                }
            }
        }

        System.out.println(
                "No existing order in progress or status is 'delivered' or 'canceled'. Proceeding with order processing.");

        try {
            // Send order request to PostgreSQL
            OrderResponse orderResponse = restTemplate.postForObject(
                    "http://34.230.32.169:8080/" + orderRequest.getMerchantId() + "/processOrder",
                    orderRequest,
                    OrderResponse.class);

            if (orderResponse != null) {
                String status = "unready";
                String claimer = "";
                boolean pointOfSale = false;
                if (orderRequest.getClaimer() != null && !orderRequest.getClaimer().isEmpty()) {
                    status = "arrived";
                    claimer = orderRequest.getClaimer();
                    pointOfSale = true;
                }

                Order order = new Order(
                    orderResponse.getName(), //HERE IS WHERE YOU NEED TO REPLACE THE ORDER ID WITH SOMETHING GENERATED
                    orderRequest.getMerchantId(),
                    orderRequest.getUserId(),
                    orderResponse.getTotalPrice(), // Using the total price from the response
                    orderResponse.getTotalPointPrice(),
                    orderResponse.getTip(),
                    orderRequest.isInAppPayments(), // Assuming this is from the request
                    convertDrinksToOrders(orderResponse.getDrinks()),
                    pointOfSale,
                    status,
                    claimer,
                    getCurrentTimestamp(),
                    session.getId()
                );

                if (!"error".equals(orderResponse.getMessageType())) {

                    jedisPooled.jsonSetWithEscape(orderKey, order);
                    System.out.println("Stored order in Redis with key: " + orderKey);

                    sendOrderResponse(session, new ResponseWrapper(
                            "create",
                            order,
                            "Order successfully processed."));
                            
                    //OrderWebSocketHandler.getInstance().sendCreateNotification(orderRequest);

                    // Broadcast the order to stations
                    Map<String, Object> data = new HashMap<>();
                    data.put("orders", Collections.singletonList(order));
                    try {
                        StationWebSocketHandler.getInstance().broadcastToMerchant(orderRequest.getMerchantId(), data);
                        System.out.println("Sent order to station");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Handle insufficient points or other errors
                    System.out.println("Order failed: " + orderResponse.getMessage());
                    sendOrderResponse(session, new ResponseWrapper(
                            "error",
                            order,
                            orderResponse.getMessage()));
                }
            }
        } catch (RestClientException e) {
            e.printStackTrace();
            sendOrderResponse(session, new ResponseWrapper(
                    "error",
                    null,
                    "Sorry, it looks like our servers are down. Check back later!"));
        }
    }

    private List<Order.DrinkOrder> convertDrinksToOrders(List<OrderResponse.DrinkOrder> drinkResponses) {
        return drinkResponses.stream()
                .map(drink -> new Order.DrinkOrder(
                        drink.getDrinkId(),
                        drink.getDrinkName(),
                        drink.getPaymentType(),  // Now using the provided paymentType
                        drink.getSizeType(),     // Now using the provided sizeType
                        drink.getQuantity()
                ))
                .toList();
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getMerchantId(), orderRequest.getUserId());
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
                    message);
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

    public void arriveOrder(WebSocketSession session, int merchantID, int userID) {

        System.out.println("ArrivingOrder order for merchantId: " + merchantID);

        // Fetch open and happyHour status from Redis
        String orderKey = merchantID + "." + userID;
        Order existingOrder = null;



        System.out.println("Parsed order key: " + orderKey);


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
                            "Failed to process existing order data."));
                    return;
                }

                // Deserialize the string back into an Order object
                try {
                    existingOrder = objectMapper.readValue(orderJson, Order.class);
                    System.out.println("Deserialized Order object: " + existingOrder);
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to deserialize existing order data: " + e.getMessage());
                    sendOrderResponse(session, new ResponseWrapper(
                            "error",
                            null,
                            "Failed to process existing order data."));
                    return;
                }

                // Check the status of the existing order
                String existingStatus = existingOrder.getStatus();
                System.out.println("Existing order status: " + existingStatus);

                if ( !"ready".equals(existingStatus) ) {
                    System.out.println("Order not ready : status=" + existingStatus);
                    sendOrderResponse(session, new ResponseWrapper(
                            "error",
                            null,
                            "Your order is not ready!"));
                    return;
                }
            }
        }

        System.out.println(
                "Order confirmed exists and is ready. Marking as arrived...");
        try {
            assert existingOrder != null;
            existingOrder.setStatus("arrived");


                    jedisPooled.jsonSetWithEscape(orderKey, existingOrder);
                    System.out.println("Re-Stored order in Redis with key: " + orderKey);

                    sendOrderResponse(session, new ResponseWrapper(
                            "update",
                            existingOrder,
                            "Marked as arrived."));

                    OrderWebSocketHandler.getInstance().sendArrivedNotification(userID, existingOrder.getClaimer() );


                    // Broadcast the order to stations
                    Map<String, Object> data = new HashMap<>();
                    data.put("orders", Collections.singletonList(existingOrder));
                    try {
                        StationWebSocketHandler.getInstance().broadcastToMerchant(existingOrder.getMerchantId(), data);
                        System.out.println("Notified Stations that person is arrived");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }



        } catch (RestClientException e) {
            e.printStackTrace();
            sendOrderResponse(session, new ResponseWrapper(
                    "error",
                    null,
                    "Sorry, it looks like our servers are down. Check back later!"));
        }

    }
}