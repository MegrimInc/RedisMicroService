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
import edu.help.websocket.TerminalWebSocketHandler;
import edu.help.websocket.OrderWebSocketHandler;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;

import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import static edu.help.config.ApiConfig.FULL_HTTP_PATH;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPooled jedisPooled; // Redis client for simple operations

    public OrderService(RestTemplate restTemplate, JedisPooled jedisPooled, JedisPool jedisPool) {
        this.restTemplate = restTemplate;
        this.jedisPooled = jedisPooled;
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for merchantId: " + orderRequest.getMerchantId());

        // Fetch the total quantity of items ordered
        int totalQuantity = orderRequest.getItems().stream()
                .mapToInt(OrderRequest.ItemOrder::getQuantity)
                .sum();

        // Determine quantity limit based on payment type
        int quantityLimit = 10;

        // Check if total quantity exceeds the limit
        if (totalQuantity > quantityLimit) {
            String message = "You can only add up to 10 items per order";

            System.out.println("Order quantity limit exceeded: " + message);

            // Send error response and exit
            sendOrderResponse(session, new ResponseWrapper("error", null, message));
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
                    FULL_HTTP_PATH + "/order/" + orderRequest.getMerchantId() + "/processOrder",
                    orderRequest,
                    OrderResponse.class);

            try {
                System.out.println("[DEBUG] OrderResponse received from backend: "
                        + objectMapper.writeValueAsString(orderResponse));
            } catch (JsonProcessingException e) {
                System.err.println("[ERROR] Failed to serialize OrderResponse: " + e.getMessage());
            }

            if (orderResponse != null) {
                Order order = new Order(
                        orderResponse.getName(),                   
                        orderRequest.getMerchantId(),
                        orderRequest.getCustomerId(),
                        orderRequest.getEmployeeId(),
                        orderResponse.getTotalPrice(), // Using the total price from the response
                        orderResponse.getTotalPointPrice(),
                        orderResponse.getTotalGratuity(),
                        orderResponse.getTotalServiceFee(),
                        orderResponse.getTotalTax(),
                        orderResponse.isInAppPayments(), // Assuming this is from the request
                        convertItemsToOrders(orderResponse.getItems()),
                        orderRequest.getPointOfSale(),
                        "unready",
                        getCurrentTimestamp(),
                        session.getId());

                if (!"error".equals(orderResponse.getMessageType())) {

                    jedisPooled.jsonSetWithEscape(orderKey, order);
                    System.out.println("Stored order in Redis with key: " + orderKey);

                    sendOrderResponse(session, new ResponseWrapper(
                            "create",
                            order,
                            "Order successfully processed."));

                    // OrderWebSocketHandler.getInstance().sendCreateNotification(orderRequest);

                    // Broadcast the order to terminals
                    Map<String, Object> data = new HashMap<>();
                    data.put("orders", Collections.singletonList(order));
                    try {
                        TerminalWebSocketHandler.getInstance().broadcastToEmployee(orderRequest.getEmployeeId(), data);
                        System.out.println("Sent order to terminal");
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

    private List<Order.ItemOrder> convertItemsToOrders(List<OrderResponse.ItemOrder> itemResponses) {
        return itemResponses.stream()
                .map(item -> new Order.ItemOrder(
                        item.getItemId(),
                        item.getItemName(),
                        item.getPaymentType(), // Now using the provided paymentType
                        item.getQuantity()))
                .toList();
    }

   private String generateOrderKey(OrderRequest orderRequest) {
    return String.format("%d.%d.%d",
        orderRequest.getMerchantId(),
        orderRequest.getEmployeeId(),
        orderRequest.getCustomerId()
    );
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

    public void refreshOrdersForCustomer(int customerId, WebSocketSession session) {
        ScanParams scanParams = new ScanParams().match("*.*." + customerId);
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
                        "No orders found for the customer."));
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

    public void arriveOrder(WebSocketSession session, int merchantId, int customerId, int employeeId) {

        System.out.println("ArrivingOrder order for merchantId: " + merchantId);

        String orderKey = merchantId + "." + employeeId + "." + customerId;
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

                if (!"ready".equals(existingStatus)) {
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

            OrderWebSocketHandler.getInstance().sendArrivedNotification(customerId, existingOrder.getEmployeeId());

            // Broadcast the order to terminals
            Map<String, Object> data = new HashMap<>();
            data.put("orders", Collections.singletonList(existingOrder));
            try {
                TerminalWebSocketHandler.getInstance().broadcastToEmployee(existingOrder.getEmployeeId(), data);
                System.out.println("Notified Terminals that person is arrived");
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