package edu.help.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.TerminalSession;
import edu.help.dto.Order;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import static edu.help.config.ApiConfig.FULL_HTTP_PATH;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static TerminalWebSocketHandler instance;
    private final JedisPooled jedisPooled;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(); // Session storage
    private final OrderWebSocketHandler orderWebSocketHandler;
    private final RestTemplate restTemplate;

    public TerminalWebSocketHandler(JedisPooled jedisPooled, JedisPool jedisPool,
            OrderWebSocketHandler orderWebSocketHandler, RestTemplate restTemplate) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
        this.orderWebSocketHandler = orderWebSocketHandler;
        instance = this;
        this.restTemplate = restTemplate;

    }

    public static TerminalWebSocketHandler getInstance() {
        return instance;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Log the received message payload
            String payload = message.getPayload();
            System.out.println("Terminal WebSocket message received: " + payload);

            // Parse the JSON message
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);

            // Extract the action from the payload
            String action = (String) payloadMap.get("action");

            // Handle the action based on its value
            System.out.println("Action received:" + action);
            switch (action) {
                case "ping":
                    handlePingAction(session, payloadMap);
                    break;

                case "initialize":
                    handleInitializeAction(session, payloadMap);
                    break;

                case "refresh":
                    handleRefreshAction(session, payloadMap);
                    break;

                case "ready":
                    handleReadyAction(session, payloadMap);
                    break;

                case "deliver":
                    handleDeliverAction(session, payloadMap);
                    break;

                case "cancel":
                    handleCancelAction(session, payloadMap);
                    break;

            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorMessage(session, "An error occurred while processing the message.");
        }
    }

    private void handlePingAction(WebSocketSession session, Map<String, Object> payload) throws IOException {
        JSONObject response = new JSONObject();
        response.put("heartbeat", "pong");
        session.sendMessage(new TextMessage(response.toString()));
    }

    @Transactional
    public void handleInitializeAction(WebSocketSession session, Map<String, Object> payload) throws Exception {

        int merchantId = (int) payload.get("merchantId");
        int employeeId = (int) payload.get("employeeId");

        System.out.println("Received employeeId: " + employeeId);
        System.out.println("Received merchantId: " + merchantId);

        // Create the Redis key
        String redisKey = String.valueOf(employeeId);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(redisKey); // Watch the key for changes

            // Check if there's an existing session for this terminal and close it
            TerminalSession existingSession = null;
            try {
                // Assuming the key holds JSON data, use jsonGet
                existingSession = jedisPooled.jsonGet(redisKey, TerminalSession.class);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (existingSession != null) {
                String existingSessionId = existingSession.getSessionId();
                WebSocketSession existingWsSession = sessionMap.get(existingSessionId);

                // Send a terminate message before closing the existing session
                if (existingWsSession != null && existingWsSession.isOpen()) {
                    JSONObject terminateMessage = new JSONObject();
                    terminateMessage.put("terminate", true);
                    existingWsSession.sendMessage(new TextMessage(terminateMessage.toString()));
                    existingWsSession.close();
                    System.out.println("Closing the connection for..." + employeeId);
                }
            }

            // Store the new session in Redis and in the session map
            TerminalSession newSession = new TerminalSession(merchantId, employeeId, session.getId());

            Transaction transaction = jedis.multi(); // Start the transaction
            System.out.println("Attempting to execute Redis transaction...");

            // Use jsonSet for storing JSON objects
            transaction.jsonSet(redisKey, objectMapper.writeValueAsString(newSession));

            System.out.println("TerminalSession stored in Redis: " + redisKey);
            List<Object> results = transaction.exec(); // Execute the transaction
            System.out.println("Transaction results: " + results);

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Initialization failed due to a conflict. Please try again.");
                return;
            }

            sessionMap.put(session.getId(), session); // Store the session in the session map
            System.out.println("TerminalSession stored in Redis: " + session);
            session.sendMessage(new TextMessage("Initialization successful for employee " + employeeId));
            handleRefreshAction(session, payload);

        }
    }

    @Transactional
    public void handleDeliverAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int merchantId = (int) payload.get("merchantId");
        int customerId = (int) payload.get("customerId");
        int employeeId = (int) payload.get("employeeId");

        String orderRedisKey = merchantId + "." + employeeId + "." + customerId;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(orderRedisKey); // Watch the key for changes

            // Retrieve the JSON object from Redis
            Object orderJsonObj = jedisPooled.jsonGet(orderRedisKey);

            if (orderJsonObj == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            // Convert the retrieved Object to a JSON string
            String orderJson = objectMapper.writeValueAsString(orderJsonObj);

            // Deserialize the JSON string into an Order object
            Order order = objectMapper.readValue(orderJson, Order.class);

            String currentStatus = order.getStatus();

            if (!"ready".equals(currentStatus) && !"arrived".equals(currentStatus)) {
                sendErrorMessage(session, "Only ready or arrived orders can be marked as delivered.");
                jedis.unwatch(); // Unwatch if the order is not ready
                return;
            }

            // Update the order to set status to "delivered"
            order.setStatus("delivered");

            Transaction transaction = jedis.multi(); // Start the transaction
            String updatedOrderJson = objectMapper.writeValueAsString(order);
            transaction.jsonSet(orderRedisKey, updatedOrderJson);
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to deliver the order due to a conflict. Please try again.");
                return;
            }

            // Send the order to PostgreSQL
            restTemplate.postForLocation(
                    FULL_HTTP_PATH + "/employee/save",
                    order);

            Map<String, Object> data = new HashMap<>();
            data.put("update", Collections.singletonList(order));

            broadcastToEmployee(employeeId, data);

            orderWebSocketHandler.updateCustomer(order);
        }
    }

    @Transactional
    public void handleCancelAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int merchantId = (int) payload.get("merchantId");
        int customerId = (int) payload.get("customerId");
        int employeeId = (int) payload.get("employeeId");

        String orderRedisKey = merchantId + "." + employeeId + "." + customerId;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(orderRedisKey); // Watch the key for changes

            // Retrieve the JSON object from Redis
            Object orderJsonObj = jedisPooled.jsonGet(orderRedisKey);

            if (orderJsonObj == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            // Convert the retrieved Object to a JSON string
            String orderJson = objectMapper.writeValueAsString(orderJsonObj);

            // Deserialize the JSON string into an Order object
            Order order = objectMapper.readValue(orderJson, Order.class);

            // Update the order status to "canceled"
            order.setStatus("canceled");

            // Start the transaction
            Transaction transaction = jedis.multi();
            // Serialize the updated Order object back to JSON and save it to Redis
            String updatedOrderJson = objectMapper.writeValueAsString(order);
            transaction.jsonSet(orderRedisKey, updatedOrderJson);
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to cancel the order due to a conflict. Please try again.");
                return;
            }

            // Send the order to PostgreSQL
            restTemplate.postForLocation(
                    FULL_HTTP_PATH + "/employee/save",
                    order);

            Map<String, Object> data = new HashMap<>();
            data.put("update", Collections.singletonList(order));

            broadcastToEmployee(employeeId, data);
            orderWebSocketHandler.updateCustomer(order);
        }
    }

    @Transactional
    public void handleReadyAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int merchantId = (int) payload.get("merchantId");
        int customerId = (int) payload.get("customerId");
        int employeeId = (int) payload.get("employeeId");

        String orderRedisKey = merchantId + "." + employeeId + "." + customerId;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(orderRedisKey); // Watch the key for changes

            // Retrieve the JSON object from Redis
            Object orderJsonObj = jedisPooled.jsonGet(orderRedisKey);

            if (orderJsonObj == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            // Convert the retrieved Object to a JSON string
            String orderJson;
            try {
                orderJson = objectMapper.writeValueAsString(orderJsonObj);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            // Deserialize the JSON string into an Order object
            Order order;
            try {
                order = objectMapper.readValue(orderJson, Order.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentStatus = order.getStatus();

            if ("ready".equals(currentStatus)) {
                sendErrorMessage(session, "Order is already marked as ready.");
                jedis.unwatch(); // Unwatch if the order is already ready
                return;
            }

            // Update the order to set status to "ready"
            order.setStatus("ready");

            Transaction transaction = jedis.multi(); // Start the transaction
            String updatedOrderJson = objectMapper.writeValueAsString(order);
            transaction.jsonSet(orderRedisKey, updatedOrderJson);
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to mark the order as ready due to a conflict. Please try again.");
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("update", Collections.singletonList(order));
            broadcastToEmployee(employeeId, data);
            orderWebSocketHandler.updateCustomer(order);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }

    @Transactional
    public void handleRefreshAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int merchantId = (int) payload.get("merchantId");
        int employeeId = (int) payload.get("employeeId");

        try (Jedis jedis = jedisPool.getResource()) {
            String keyPattern = merchantId + "." + employeeId + ".*";
            // Prepare to scan Redis for keys matching the pattern
            ScanParams scanParams = new ScanParams().match(keyPattern);
            String cursor = "0";

            List<Order> orders = new ArrayList<>();

            System.out.println("Scanning Redis with pattern: " + keyPattern);
            System.out.println("Initial cursor value: " + cursor);

            do {
                // Scan for keys with the given pattern
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                List<String> keys = scanResult.getResult();

                // Print out the keys being scanned
                System.out.println("Keys scanned in this iteration:");
                for (String key : keys) {
                    System.out.println(" - " + key);
                }

                for (String key : keys) {
                    // Match the format merchantId.# and ensure the part after the dot is entirely
                    // numeric
                    if (key.matches(merchantId + "\\." + employeeId + "\\.\\d+")) {
                        try {
                            // Retrieve the JSON object from Redis
                            Object orderJsonObj = jedisPooled.jsonGet(key);

                            if (orderJsonObj == null) {
                                System.out.println("Order does not exist for key: " + key);
                                continue;
                            }

                            // Convert the retrieved Object directly to an Order object
                            Order order = objectMapper.convertValue(orderJsonObj, Order.class);

                            // Exclude session-related data
                            if (!orderJsonObj.toString().contains("active")) {
                                orders.add(order);
                            } else {
                                System.out.println("Skipping session-related data for key Id: " + key);
                            }
                        } catch (JedisDataException e) {
                            System.err.println(
                                    "Encountered JedisDataException for key: " + key + " - " + e.getMessage());
                            // Optionally handle recovery or cleanup here
                        }
                    } else {
                        System.out.println("Skipping key that does not match format: " + key);
                    }
                }
            } while (!"0".equals(cursor)); // Continue scanning until cursor is "0"

            // Convert the list of Order objects to a Map and send it to the client
            if (!orders.isEmpty()) {
                List<Map<String, Object>> ordersData = new ArrayList<>();
                for (Order order : orders) {
                    Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
                    ordersData.add(orderData);
                    // Debug: Log each order being sent
                    System.out.println("Converted Order to Map: " + orderData);
                }

                // Create a map with the key "orders" and value as the list of order maps
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("orders", ordersData);

                // Convert the map to a JSON string
                String ordersJsonArray = objectMapper.writeValueAsString(responseMap);
                System.out.println("Final JSON being sent: " + ordersJsonArray); // Debug: Log the final JSON string
                session.sendMessage(new TextMessage(ordersJsonArray));
            } else {
                System.out.println("No orders found, sending empty list."); // Debug: Log if no orders found
                session.sendMessage(new TextMessage("{\"orders\":[]}"));
            }

        } catch (Exception e) {
            e.printStackTrace(); // Handle exceptions
            sendErrorMessage(session, "Error retrieving orders");
        }

    }

    public void broadcastToEmployee(int employeeId, Map<String, Object> data) throws IOException {
        // Prepare the data to be broadcasted
        String message = objectMapper.writeValueAsString(data);

        // Debug: Print the message that is being broadcasted
        System.out.println("Broadcasting message to employee " + employeeId + ": " + message);

        // Step 1: Scan Redis for keys matching the format merchantId.AlphabeticLetter
        String pattern = String.valueOf(employeeId);

        List<String> matchingSessionIds = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            ScanParams scanParams = new ScanParams().match(pattern);
            String cursor = "0";

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();

                for (String key : scanResult.getResult()) {
                    // Step 2: Retrieve the TerminalSession object from Redis
                    TerminalSession terminalSession = jedisPooled.jsonGet(key, TerminalSession.class);

                    if (terminalSession != null // && terminalSession.getActive()
                    ) {
                        matchingSessionIds.add(terminalSession.getSessionId());
                    }
                }
            } while (!"0".equals(cursor));
        }

        // Debug: Print the matching session Ids
        System.out.println("Matching session Id for employee " + employeeId + ": " + matchingSessionIds);

        // Step 3: Filter the sessionMap to find open sessions that match the retrieved
        // sessionIds
        for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession wsSession = entry.getValue();

            if (matchingSessionIds.contains(sessionId) && wsSession.isOpen()) {
                // Debug: Print the session Id before sending the message
                System.out.println("Sending message to session Id: " + sessionId);
                wsSession.sendMessage(new TextMessage(message));
            } else {
                // Debug: If session is not open or does not match, log it
                System.out.println("Session Id: " + sessionId + " is not open or does not match. Skipping.");
            }
        }
    }
}