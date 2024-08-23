package edu.help.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import edu.help.dto.BartenderSession;
import edu.help.dto.Order;
import edu.help.service.RedisOrderService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {

    private static BartenderWebSocketHandler instance;
    private final JedisPooled jedisPooled;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(); // Session storage
    private final OrderWebSocketHandler orderWebSocketHandler;
    private final RestTemplate restTemplate;
    private final RedisOrderService redisOrderService;

    public BartenderWebSocketHandler(JedisPooled jedisPooled, JedisPool jedisPool,
            OrderWebSocketHandler orderWebSocketHandler, RestTemplate restTemplate, RedisOrderService redisOrderService) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
        this.orderWebSocketHandler = orderWebSocketHandler;
        instance = this;
        this.restTemplate = restTemplate;
        this.redisOrderService = redisOrderService;

    }

    // Getter for the singleton instance
    public static BartenderWebSocketHandler getInstance() {
        return instance;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Log the received message payload
            String payload = message.getPayload();
            System.out.println("Bartender WebSocket message received: " + payload);

            System.out.println("Let me confirm you work");

            // Parse the JSON message
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);

            // Extract the action from the payload
            String action = (String) payloadMap.get("action");

            // Handle the action based on its value
            switch (action) {
                case "initialize":
                    handleInitializeAction(session, payloadMap);
                    break;

                case "refresh":
                    handleRefreshAction(session, payloadMap);
                    break;

                case "claim":
                    handleClaimAction(session, payloadMap);
                    break;

                case "unclaim":
                    handleUnclaimAction(session, payloadMap);
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

                case "disable":
                    handleDisableAction(session, payloadMap);
                    break;

                case "open":
                    // ADD CHECK HERE TO BAR, INCLUDE RACE CONDITIONS.
                    int barID4 = (int) payloadMap.get("barID");

                    // Prepare the data to be broadcasted to all bartenders
                    Map<String, Object> openPayload = new HashMap<>();
                    openPayload.put("barStatus", true);

                    // Broadcast the bar open status to all bartenders
                    broadcastToBar(barID4, openPayload);

                    // No need to send a separate response to the bartender who initiated the open action
                    break;

                case "close":
                    // ADD CHECK HERE TO BAR, INCLUDE RACE CONDITIONS
                    int barID0 = (int) payloadMap.get("barID");

                    // Prepare the data to be broadcasted to all bartenders
                    Map<String, Object> closePayload0 = new HashMap<>();
                    closePayload0.put("barStatus", false);

                    // Broadcast the bar close status to all bartenders
                    broadcastToBar(barID0, closePayload0);

                    // No need to send a separate response to the bartender who initiated the close action
                    break;

                default:
                    sendErrorMessage(session, "Unknown action: " + action);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorMessage(session, "An error occurred while processing the message.");
        }
    }

    private void notifyBartendersOfActiveConnections(int barID) {
        System.out.println("notifyBartendersOfActiveConnections triggered with barID: " + barID);

        String pattern = barID + ".[a-zA-Z]*";
        System.out.println("Searching for keys with pattern: " + pattern);

        try (Jedis jedis = jedisPool.getResource()) {
            // Find all bartender keys for the given barID
            Set<String> bartenderKeys = jedis.keys(pattern);
            System.out.println("Found bartender keys: " + bartenderKeys);

            // Filter bartenders with getActive() == true and where bartenderID is alphabetic
            List<BartenderSession> acceptingBartenders = new ArrayList<>();
            for (String key : bartenderKeys) {
                System.out.println("Processing key: " + key);
                String[] parts = key.split("\\.");
                if (parts.length != 2) {
                    System.out.println("Invalid key format: " + key);
                    continue;
                }

                String bartenderID = parts[1];
                System.out.println("Found bartenderID: " + bartenderID);
                if (bartenderID.matches("[a-zA-Z]+")) { // Ensure bartenderID is alphabetic
                    BartenderSession bartenderSession = null;
                    try {
                        // Deserialize the stored JSON into a BartenderSession object
                        bartenderSession = jedisPooled.jsonGet(key, BartenderSession.class);
                        System.out.println("Deserialized BartenderSession: " + bartenderSession);
                    } catch (Exception e) {
                        System.out.println("Failed to deserialize key: " + key);
                        e.printStackTrace();
                        continue; // Skip if deserialization fails
                    }

                    if (bartenderSession != null && bartenderSession.getActive() && sessionMap.get(bartenderSession.getSessionId()) != null) { // Changed from isActive() to getActive()
                        System.out.println("Bartender is active: " + bartenderID);
                        acceptingBartenders.add(bartenderSession);
                    }
                }
            }

            System.out.println("Total bartenders active: " + acceptingBartenders.size());

            // Send a message to each bartender
            for (int i = 0; i < acceptingBartenders.size(); i++) {
                BartenderSession bartenderSession = acceptingBartenders.get(i);
                String sessionId = bartenderSession.getSessionId();
                WebSocketSession wsSession = sessionMap.get(sessionId);

                if (wsSession != null && wsSession.isOpen()) {
                    System.out.println("Sending update to bartender: " + bartenderSession.getBartenderId());

                    // Create the message using Map<String, String>
                    Map<String, String> updateMessage = new HashMap<>();
                    updateMessage.put("updateTerminal", bartenderSession.getBartenderId());
                    updateMessage.put("bartenderCount", String.valueOf(acceptingBartenders.size()));
                    updateMessage.put("bartenderNumber", String.valueOf(i));

                    // Convert the map to a JSON string using ObjectMapper
                    String jsonMessage = objectMapper.writeValueAsString(updateMessage);

                    // Send the message to the WebSocket session
                    wsSession.sendMessage(new TextMessage(jsonMessage));
                } else {
                    System.out.println(
                            "WebSocket session is closed or null for bartender: " + bartenderSession.getBartenderId());
                }
            }
        } catch (Exception e) {
            // Handle exceptions, such as JSON processing or WebSocket issues
            System.out.println("Exception in notifyBartendersOfActiveConnections: ");
            e.printStackTrace();
        }
    }

    @Transactional
    private void handleInitializeAction(WebSocketSession session, Map<String, Object> payload) throws Exception {

        int barID = (int) payload.get("barID");
        String bartenderID = (String) payload.get("bartenderID");

        System.out.println("Received bartenderID: " + bartenderID);
        System.out.println("Received barID: " + barID);

        if (bartenderID == null || bartenderID.isEmpty() || !bartenderID.matches("[a-zA-Z]+")) {
            // Send an error response
            sendErrorMessage(session,
                    "Initialization Failed: Invalid bartenderID. It must be non-empty and contain only alphabetic characters.");
            return;
        }

        // Create the Redis key
        String redisKey = barID + "." + bartenderID;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(redisKey); // Watch the key for changes

            // Check if there's an existing session for this bartender and close it
            BartenderSession existingSession = null;
            try {
                // Assuming the key holds JSON data, use jsonGet
                existingSession = jedisPooled.jsonGet(redisKey, BartenderSession.class);
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
                    System.out.println("Closing the connection for..." + bartenderID);
                }
            }

            // Store the new session in Redis and in the session map
            BartenderSession newSession = new BartenderSession(barID, bartenderID, session.getId(), true);

            Transaction transaction = jedis.multi(); // Start the transaction
            System.out.println("Attempting to execute Redis transaction...");

            // Use jsonSet for storing JSON objects
            transaction.jsonSet(redisKey, objectMapper.writeValueAsString(newSession));

            System.out.println("BartenderSession stored in Redis: " + redisKey);
            List<Object> results = transaction.exec(); // Execute the transaction
            System.out.println("Transaction results: " + results);

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Initialization failed due to a conflict. Please try again.");
                return;
            }

            sessionMap.put(session.getId(), session); // Store the session in the session map
            System.out.println("BartenderSession stored in Redis: " + session);
            session.sendMessage(new TextMessage("Initialization successful for bartender " + bartenderID));

            // Notify each bartender of active WebSocket connections
            notifyBartendersOfActiveConnections(barID);
            handleRefreshAction(session, payload);
        }
    }

    @Transactional
private void handleDeliverAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
    int barID = (int) payload.get("barID");
    int orderID = (int) payload.get("orderID");
    String bartenderID = (String) payload.get("bartenderID");

    String orderRedisKey = barID + "." + orderID;

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

        String currentClaimer = order.getClaimer();

        if (!bartenderID.equals(currentClaimer)) {
            sendErrorMessage(session, "You cannot deliver this order because it was claimed by another bartender.");
            jedis.unwatch(); // Unwatch if the order was claimed by another bartender
            return;
        }

        String currentStatus = order.getStatus();

        if (!"ready".equals(currentStatus)) {
            sendErrorMessage(session, "Only ready orders can be marked as delivered.");
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
        redisOrderService.sendOrderToPostgres(order);

        // Broadcast the updated order to all bartenders
        Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
        broadcastToBar(barID, orderData);

        orderWebSocketHandler.updateUser(orderData);
    }
}


@Transactional
private void handleCancelAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
    int barID = (int) payload.get("barID");
    int orderID = (int) payload.get("orderID");
    String cancelingBartenderID = (String) payload.get("bartenderID");

    String orderRedisKey = barID + "." + orderID;

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

        // Check if the canceling bartender is the one who claimed the order
        String currentClaimer = order.getClaimer();
        if (!cancelingBartenderID.equals(currentClaimer)) {
            sendErrorMessage(session, "You cannot cancel this order as it was claimed by another bartender.");
            jedis.unwatch(); // Unwatch if the order was claimed by another bartender
            return;
        }

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
        redisOrderService.sendOrderToPostgres(order);

        // Broadcast the updated order to all bartenders
        Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
        broadcastToBar(barID, orderData);

        orderWebSocketHandler.updateUser(orderData);
    }
}


    @Transactional
    private void handleClaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int orderID = (int) payload.get("orderID");
        String claimingBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + orderID;

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

            // Check if the order is already claimed
            String currentClaimer = order.getClaimer();
            if (currentClaimer != null && !currentClaimer.isEmpty()) {
                sendErrorMessage(session, "Order is already claimed.");
                jedis.unwatch(); // Unwatch if the order is already claimed
                return;
            }

            // Check if the order is canceled
            String currentStatus = order.getStatus();
            if ("canceled".equals(currentStatus)) {
                sendErrorMessage(session, "You cannot claim a canceled order.");
                jedis.unwatch(); // Unwatch if the order is canceled
                return;
            }

            // Update the order with the new claimer
            order.setClaimer(claimingBartenderID);

            // Start the transaction
            Transaction transaction = jedis.multi();
            // Serialize the updated Order object back to JSON and save it to Redis
            String updatedOrderJson = objectMapper.writeValueAsString(order);
            transaction.jsonSet(orderRedisKey, updatedOrderJson);
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to claim the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
            broadcastToBar(barID, orderData);

            orderWebSocketHandler.updateUser(orderData);

        }
    }

    @Transactional
    private void handleReadyAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int orderID = (int) payload.get("orderID");
        String readyBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + orderID;

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

            String currentClaimer = order.getClaimer();

            if (!readyBartenderID.equals(currentClaimer)) {
                sendErrorMessage(session,
                        "You cannot mark this order as ready because it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
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

            // Broadcast the updated order to all bartenders
            Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
            broadcastToBar(barID, orderData);

            orderWebSocketHandler.updateUser(orderData);
        }
    }

    @Transactional
    private void handleUnclaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int orderID = (int) payload.get("orderID");
        String unclaimingBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + orderID;

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

            String currentClaimer = order.getClaimer();

            if (!unclaimingBartenderID.equals(currentClaimer)) {
                sendErrorMessage(session, "You cannot unclaim this order because it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
                return;
            }

            // Update the order to remove the claimer
            order.setClaimer("");

            Transaction transaction = jedis.multi(); // Start the transaction
            String updatedOrderJson = objectMapper.writeValueAsString(order);
            transaction.jsonSet(orderRedisKey, updatedOrderJson);
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to unclaim the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
            broadcastToBar(barID, orderData);

            orderWebSocketHandler.updateUser(orderData);
        }
    }

    @Transactional
    private void handleDisableAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        String bartenderID = (String) payload.get("bartenderID");

        if (bartenderID == null || bartenderID.isEmpty() || !bartenderID.matches("[a-zA-Z]+")) {
            // Send an error response
            sendErrorMessage(session, "Invalid bartenderID. It must be non-empty and contain only alphabetic characters.");
            return;
        }

        // Create the Redis key
        String redisKey = barID + "." + bartenderID;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.watch(redisKey); // Watch the key for changes

            // Retrieve the JSON object from Redis
            Object bartenderJsonObj = jedisPooled.jsonGet(redisKey);

            if (bartenderJsonObj == null) {
                sendErrorMessage(session, "No active session found for bartender " + bartenderID + ".");
                jedis.unwatch(); // Unwatch if the data doesn't exist
                return;
            }

            // Convert the retrieved Object to a JSON string
            String bartenderJson;
            try {
                bartenderJson = objectMapper.writeValueAsString(bartenderJsonObj);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process session data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            // Deserialize the JSON string into a Map
            Map<String, Object> existingSessionData;
            try {
                existingSessionData = objectMapper.readValue(bartenderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process session data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            // Set isAcceptingOrders to FALSE
            existingSessionData.put("active", false);

            // Start a transaction and update the Redis entry
            Transaction transaction = jedis.multi();
            transaction.jsonSet(redisKey, objectMapper.writeValueAsString(existingSessionData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to disable the bartender due to a conflict. Please try again.");
                return;
            }

            // Send a success response to the client
            session.sendMessage(new TextMessage("{\"disable\":\"true\"}"));

            // Notify all bartenders of active WebSocket connections
            notifyBartendersOfActiveConnections(barID);
        } catch (Exception e) {
            // Handle any exceptions that occur during the process
            e.printStackTrace();
            sendErrorMessage(session, "An error occurred while disabling the bartender. Please try again.");
        }
    }


    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }

    

    @Transactional
    private void handleRefreshAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");

        try (Jedis jedis = jedisPool.getResource()) {
            // Prepare to scan Redis for keys matching the pattern
            ScanParams scanParams = new ScanParams().match(barID + ".*");
            String cursor = "0";

            List<Order> orders = new ArrayList<>();

            System.out.println("Scanning Redis with pattern: " + barID + ".*");
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

                // Process each key, but only if it matches the format barId.# (where # is a number)
                for (String key : keys) {
                    // Match the format barId.# and ensure the part after the dot is entirely numeric
                    if (key.matches(barID + "\\.\\d+")) {  // This regex matches barID.# where # is one or more digits
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
                                System.out.println("Skipping session-related data for key: " + key);
                            }
                        } catch (JedisDataException e) {
                            System.err.println("Encountered JedisDataException for key: " + key + " - " + e.getMessage());
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



    public void broadcastToBar(int barID, Map<String, Object> data) throws IOException {
        // Prepare the data to be broadcasted
        String message = objectMapper.writeValueAsString(data);

        // Iterate over all connected sessions and send the message
        for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
            String sessionID = entry.getKey();
            WebSocketSession wsSession = entry.getValue();

            if (wsSession.isOpen()) {
                wsSession.sendMessage(new TextMessage(message));
            }
        }
    }

}
