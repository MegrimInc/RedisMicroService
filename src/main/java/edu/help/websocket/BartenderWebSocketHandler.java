package edu.help.websocket;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import edu.help.dto.Order;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {

    private final JedisPooled jedisPooled;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>(); // Session storage

    public BartenderWebSocketHandler(JedisPooled jedisPooled, JedisPool jedisPool) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Log the received message payload
        System.out.println("Bartender WebSocket message received: " + message.getPayload());

        // Parse the JSON message
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

        String action = (String) payload.get("action");
        switch (action) {
            case "initialize":
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

                    // Check if there's an existing session for this bartender and close it
                    String existingSessionJson = (String) jedisPooled.jsonGet(redisKey);
                    if (existingSessionJson != null) {
                        // Parse the existing session data
                        Map<String, Object> existingSessionData = objectMapper.readValue(existingSessionJson, Map.class);
                        String existingSessionId = (String) existingSessionData.get("sessionId");
                        WebSocketSession existingSession = sessionMap.get(existingSessionId);

                        // Send a terminate message before closing the existing session
                        if (existingSession != null && existingSession.isOpen()) {
                            JSONObject terminateMessage = new JSONObject();
                            terminateMessage.put("key", "terminate");
                            terminateMessage.put("value", bartenderID);
                            existingSession.sendMessage(new TextMessage(terminateMessage.toString()));
                            existingSession.close();
                        }
                    }

                    // Store the new session in Redis and in the session map
                    Map<String, Object> redisValue = new HashMap<>();
                    redisValue.put("sessionId", session.getId()); // Store session ID or other relevant info
                    redisValue.put("active", true); // Boolean value

                    Transaction transaction = jedis.multi(); // Start the transaction
                    jedisPooled.jsonSet(redisKey, objectMapper.writeValueAsString(redisValue));
                    List<Object> results = transaction.exec(); // Execute the transaction

                    if (results == null || results.isEmpty()) {
                        sendErrorMessage(session, "Failed to initialize session due to a conflict. Please try again.");
                        return;
                    }

                    sessionMap.put(session.getId(), session); // Store the session in the session map
                    session.sendMessage(new TextMessage("Initialization successful for bartender " + bartenderID));

                    // Placeholder to notify each bartender of active WebSocket connections
                    notifyBartendersOfActiveConnections( barID );

                }
                break;


            case "refresh":
                int barID2 = (int) payload.get("barID");

                List<Order> orders = getOrdersFromDataSource(barID2);

                // Convert the list of orders to JSON and send it to the client
                String ordersJson = objectMapper.writeValueAsString(orders);
                session.sendMessage(new TextMessage(ordersJson));
                break;

            case "claim":
                handleClaimAction(session, payload);
                break;

            case "unclaim":
                handleUnclaimAction(session, payload);
                break;

            case "ready":
                handleReadyAction(session, payload);
                break;

            case "deliver":
                handleDeliverAction(session, payload);
                break;

            case "cancel":
                handleCancelAction(session, payload);
                break;

            case "disable":
                int barID00 = (int) payload.get("barID");
                String bartenderID00 = (String) payload.get("bartenderID");

                if (bartenderID00 == null || bartenderID00.isEmpty() || !bartenderID00.matches("[a-zA-Z]+")) {
                    // Send an error response
                    sendErrorMessage(session, "Invalid bartenderID. It must be non-empty and contain only alphabetic characters.");
                    return;
                }

                // Create the Redis key
                String redisKey00 = barID00 + "." + bartenderID00;

                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.watch(redisKey00); // Watch the key for changes

                    // Retrieve the existing session data for this bartender
                    String existingSessionJson = (String) jedisPooled.jsonGet(redisKey00);
                    if (existingSessionJson != null) {
                        // Parse the existing session data
                        Map<String, Object> existingSessionData = objectMapper.readValue(existingSessionJson, Map.class);

                        // Set isAcceptingOrders to FALSE
                        existingSessionData.put("isAcceptingOrders", false);

                        // Start a transaction and update the Redis entry
                        Transaction transaction = jedis.multi(); // Start the transaction
                        jedisPooled.jsonSet(redisKey00, objectMapper.writeValueAsString(existingSessionData));
                        List<Object> results = transaction.exec(); // Execute the transaction

                        if (results == null || results.isEmpty()) {
                            sendErrorMessage(session, "Failed to disable the bartender due to a conflict. Please try again.");
                            return;
                        }

                        session.sendMessage(new TextMessage("Bartender " + bartenderID00 + " is now disabled."));

                        // Notify all bartenders of active WebSocket connections
                        notifyBartendersOfActiveConnections(barID00);
                    } else {
                        sendErrorMessage(session, "No active session found for bartender " + bartenderID00 + ".");
                    }
                } catch (Exception e) {
                    // Handle any exceptions that occur during the process
                    e.printStackTrace();
                    sendErrorMessage(session, "An error occurred while disabling the bartender. Please try again.");
                }
                break;


            case "open":
                // ADD CHECK HERE TO BAR, INCLUDE RACE CONDITIONS.
                int barID4 = (int) payload.get("barID");

                // Prepare the data to be broadcasted to all bartenders
                Map<String, Object> closePayload = new HashMap<>();
                closePayload.put("barStatus", true);

                // Broadcast the bar close status to all bartenders
                broadcastToBar(barID4, closePayload);

                // No need to send a separate response to the bartender who initiated the close action
                break;

            case "close":
                // ADD CHECK HERE TO BAR, INCLUDE RACE CONDITIONS
                int barID0 = (int) payload.get("barID");

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
    }

    private void notifyBartendersOfActiveConnections(int barID) {
        String pattern = barID + ".*";

        try (Jedis jedis = jedisPool.getResource()) {
            // Find all bartender keys for the given barID
            Set<String> bartenderKeys = jedis.keys(pattern);

            // Filter bartenders with isAcceptingOrders == true and where bartenderID is alphabetic
            List<Map<String, Object>> acceptingBartenders = new ArrayList<>();
            for (String key : bartenderKeys) {
                String[] parts = key.split("\\.");
                if (parts.length != 2) continue;

                String bartenderID = parts[1];
                if (bartenderID.matches("[a-zA-Z]+")) {  // Ensure bartenderID is alphabetic
                    String bartenderJson = (String) jedisPooled.jsonGet(key);
                    if (bartenderJson != null) {
                        Map<String, Object> bartenderData = objectMapper.readValue(bartenderJson, Map.class);
                        Boolean isAcceptingOrders = (Boolean) bartenderData.get("isAcceptingOrders");

                        if (isAcceptingOrders != null && isAcceptingOrders) {
                            acceptingBartenders.add(bartenderData);
                        }
                    }
                }
            }

            // Send a message to each bartender
            for (int i = 0; i < acceptingBartenders.size(); i++) {
                Map<String, Object> bartenderData = acceptingBartenders.get(i);
                String bartenderID = (String) bartenderData.get("bartenderID");
                String sessionId = (String) bartenderData.get("sessionId");
                WebSocketSession bartenderSession = sessionMap.get(sessionId);

                if (bartenderSession != null && bartenderSession.isOpen()) {
                    // Create the message using Map<String, String>
                    Map<String, String> updateMessage = new HashMap<>();
                    updateMessage.put("updateTerminal", bartenderID);
                    updateMessage.put("bartenderCount", String.valueOf(acceptingBartenders.size()));
                    updateMessage.put("bartenderNumber", String.valueOf(i));

                    // Convert the map to a JSON string using ObjectMapper
                    String jsonMessage = objectMapper.writeValueAsString(updateMessage);

                    // Send the message to the WebSocket session
                    bartenderSession.sendMessage(new TextMessage(jsonMessage));
                }
            }
        } catch (Exception e) {
            // Handle exceptions, such as JSON processing or WebSocket issues
            e.printStackTrace();
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

            String orderJson = (String) jedisPooled.jsonGet(orderRedisKey);

            if (orderJson == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            Map<String, Object> orderData;
            try {
                orderData = objectMapper.readValue(orderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentClaimer = (String) orderData.get("claimer");
            String currentStatus = (String) orderData.get("status");

            if (!"ready".equals(currentStatus)) {
                sendErrorMessage(session, "Only orders that are ready can be canceled.");
                jedis.unwatch(); // Unwatch if the order is not ready
                return;
            }

            if (!cancelingBartenderID.equals(currentClaimer)) {
                sendErrorMessage(session, "You cannot cancel this order as it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
                return;
            }

            // Update the order status to "canceled"
            orderData.put("status", "canceled");

            Transaction transaction = jedis.multi(); // Start the transaction
            jedisPooled.jsonSet(orderRedisKey, objectMapper.writeValueAsString(orderData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to cancel the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            broadcastToBar(barID, orderData);
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

            String orderJson = (String) jedisPooled.jsonGet(orderRedisKey);

            if (orderJson == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            Map<String, Object> orderData;
            try {
                orderData = objectMapper.readValue(orderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentClaimer = (String) orderData.get("claimer");

            if (!bartenderID.equals(currentClaimer)) {
                sendErrorMessage(session, "You cannot deliver this order because it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
                return;
            }

            String currentStatus = (String) orderData.get("status");

            if (!"ready".equals(currentStatus)) {
                sendErrorMessage(session, "Only ready orders can be marked as delivered.");
                jedis.unwatch(); // Unwatch if the order is not ready
                return;
            }

            // Update the order to set status to "delivered"
            orderData.put("status", "delivered");

            Transaction transaction = jedis.multi(); // Start the transaction
            jedisPooled.jsonSet(orderRedisKey, objectMapper.writeValueAsString(orderData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to deliver the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            broadcastToBar(barID, orderData);

            //Saarthak's code goes here
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

            String orderJson = (String) jedisPooled.jsonGet(orderRedisKey);

            if (orderJson == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            Map<String, Object> orderData;
            try {
                orderData = objectMapper.readValue(orderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentClaimer = (String) orderData.get("claimer");

            if (currentClaimer != null && !currentClaimer.isEmpty()) {
                sendErrorMessage(session, "Order is already claimed.");
                jedis.unwatch(); // Unwatch if the order is already claimed
                return;
            }

            String currentStatus = (String) orderData.get("status");

            if ("canceled".equals(currentStatus)) {
                sendErrorMessage(session, "You cannot claim a canceled order.");
                jedis.unwatch(); // Unwatch if the order is canceled
                return;
            }

            // Update the order with the new claimer
            orderData.put("claimer", claimingBartenderID);

            Transaction transaction = jedis.multi(); // Start the transaction
            jedisPooled.jsonSet(orderRedisKey, objectMapper.writeValueAsString(orderData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to claim the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            broadcastToBar(barID, orderData);
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

            String orderJson = (String) jedisPooled.jsonGet(orderRedisKey);

            if (orderJson == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            Map<String, Object> orderData;
            try {
                orderData = objectMapper.readValue(orderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentClaimer = (String) orderData.get("claimer");

            if (!readyBartenderID.equals(currentClaimer)) {
                sendErrorMessage(session, "You cannot mark this order as ready because it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
                return;
            }

            String currentStatus = (String) orderData.get("status");

            if ("ready".equals(currentStatus)) {
                sendErrorMessage(session, "Order is already marked as ready.");
                jedis.unwatch(); // Unwatch if the order is already ready
                return;
            }

            // Update the order to set status to "ready"
            orderData.put("status", "ready");

            Transaction transaction = jedis.multi(); // Start the transaction
            jedisPooled.jsonSet(orderRedisKey, objectMapper.writeValueAsString(orderData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to mark the order as ready due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            broadcastToBar(barID, orderData);
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

            String orderJson = (String) jedisPooled.jsonGet(orderRedisKey);

            if (orderJson == null) {
                sendErrorMessage(session, "Order does not exist.");
                jedis.unwatch(); // Unwatch if the order doesn't exist
                return;
            }

            Map<String, Object> orderData;
            try {
                orderData = objectMapper.readValue(orderJson, Map.class);
            } catch (JsonProcessingException e) {
                sendErrorMessage(session, "Failed to process order data.");
                jedis.unwatch(); // Unwatch if processing fails
                return;
            }

            String currentClaimer = (String) orderData.get("claimer");

            if (!unclaimingBartenderID.equals(currentClaimer)) {
                sendErrorMessage(session, "You cannot unclaim this order because it was claimed by another bartender.");
                jedis.unwatch(); // Unwatch if the order was claimed by another bartender
                return;
            }

            // Update the order to remove the claimer
            orderData.put("claimer", "");

            Transaction transaction = jedis.multi(); // Start the transaction
            jedisPooled.jsonSet(orderRedisKey, objectMapper.writeValueAsString(orderData));
            List<Object> results = transaction.exec(); // Execute the transaction

            if (results == null || results.isEmpty()) {
                sendErrorMessage(session, "Failed to unclaim the order due to a conflict. Please try again.");
                return;
            }

            // Broadcast the updated order to all bartenders
            broadcastToBar(barID, orderData);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }

    private List<Order> getOrdersFromDataSource(int barID) {
        try (Jedis jedis = jedisPool.getResource()) {
            ScanParams scanParams = new ScanParams().match(barID + ".*");
            String cursor = "0";
            List<Order> orders = new ArrayList<>();

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                List<String> keys = scanResult.getResult();

                for (String key : keys) {
                    String orderJson = (String) jedisPooled.jsonGet(key);
                    if (orderJson != null) {
                        Order order = objectMapper.readValue(orderJson, Order.class);
                        orders.add(order);
                    }
                }
            } while (!"0".equals(cursor));

            return orders;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private void broadcastToBar(int barID, Map<String, Object> data) throws IOException {
        // Prepare the data to be broadcasted
        String message = objectMapper.writeValueAsString(data);

        // Iterate over all connected sessions and send the message
        for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
            String sessionID = entry.getKey();
            WebSocketSession wsSession = entry.getValue();

            if (wsSession.isOpen() ) {
                wsSession.sendMessage(new TextMessage(message));
            }
        }
    }


}