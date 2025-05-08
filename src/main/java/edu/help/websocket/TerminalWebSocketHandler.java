    package edu.help.websocket;

    import java.io.IOException;
    import java.util.ArrayList;
    import java.util.Collections;
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

    import edu.help.dto.TerminalSession;
    import edu.help.dto.Order;
    import redis.clients.jedis.Jedis;
    import redis.clients.jedis.JedisPool;
    import redis.clients.jedis.JedisPooled;
    import redis.clients.jedis.Transaction;
    import redis.clients.jedis.exceptions.JedisDataException;
    import redis.clients.jedis.params.ScanParams;
    import redis.clients.jedis.resps.ScanResult;

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
                        int merchantId = (int) payloadMap.get("merchantId");
                        String merchantKey = String.valueOf(merchantId); // Adjust this to match your specific key format

                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.watch(merchantKey);

                            // Check if the merchant is already open
                            boolean isMerchantOpen = Boolean.parseBoolean(jedis.hget(merchantKey, "open"));
                            if (isMerchantOpen) {
                                sendErrorMessage(session, "Merchant is already open.");
                                jedis.unwatch();
                                break;
                            }

                            Transaction transaction = jedis.multi();
                            transaction.hset(merchantKey, "open", "true");
                            // Include any other fields that need to be set during opening
                            List<Object> results = transaction.exec();

                            if (results != null) {
                                // Notify all terminals if the transaction was successful
                                Map<String, Object> openPayload = new HashMap<>();
                                openPayload.put("merchantStatus", true);

                                // Convert the map to a JSON string (if needed)

                                // Use the existing broadcastToMerchant method to notify all terminals
                                broadcastToMerchant(merchantId, openPayload);
                            } else {
                                sendErrorMessage(session, "Failed to open the merchant due to a race condition.");
                            }

                        } catch (Exception e) {
                            sendErrorMessage(session, "An error occurred while opening the merchant.");
                        }
                        break;

                    case "close":
                        int merchantId2 = (int) payloadMap.get("merchantId");
                        String merchantKey2 = String.valueOf(merchantId2); // Adjust this to match your specific key format

                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.watch(merchantKey2);

                            // Check if the merchant is already closed
                            boolean isMerchantClosed = !Boolean.parseBoolean(jedis.hget(merchantKey2, "open"));
                            if (isMerchantClosed) {
                                sendErrorMessage(session, "Merchant is already closed.");
                                jedis.unwatch();
                                break;
                            }

                            Transaction transaction = jedis.multi();
                            transaction.hset(merchantKey2, "open", "false");
                            // Include any other fields that need to be set during closing
                            List<Object> results = transaction.exec();

                            if (results != null) {
                                // Notify all terminals if the transaction was successful
                                Map<String, Object> closePayload = new HashMap<>();
                                closePayload.put("merchantStatus", false);

                                // Use the existing broadcastToMerchant method to notify all terminals
                                broadcastToMerchant(merchantId2, closePayload);
                            } else {
                                sendErrorMessage(session, "Failed to close the merchant due to a race condition.");
                            }

                        } catch (Exception e) {
                            sendErrorMessage(session, "An error occurred while closing the merchant.");
                        }
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

        private void handlePingAction(WebSocketSession session, Map<String, Object> payload) throws IOException {
            JSONObject response = new JSONObject();
            response.put("heartbeat", "pong");
            session.sendMessage(new TextMessage(response.toString()));
        }

        private void notifyTerminalsOfActiveConnections(int merchantId) {
            System.out.println("notifyTerminalsOfActiveConnections triggered with merchantId: " + merchantId);

            String pattern = merchantId + ".[a-zA-Z]*";
            System.out.println("Searching for keys with pattern Id: " + pattern);

            try (Jedis jedis = jedisPool.getResource()) {
                // Find all terminal keys for the given merchantId
                Set<String> terminalKeys = jedis.keys(pattern);
                System.out.println("Found terminal keys: " + terminalKeys);

                // Filter terminals with getActive() == true and where terminalId is alphabetic
                List<TerminalSession> acceptingTerminals = new ArrayList<>();
                for (String key : terminalKeys) {
                    System.out.println("Processing key: " + key);
                    String[] parts = key.split("\\.");
                    if (parts.length != 2) {
                        System.out.println("Invalid key format: " + key);
                        continue;
                    }

                    String terminalId = parts[1];
                    System.out.println("Found terminalId: " + terminalId);
                    if (terminalId.matches("[a-zA-Z]+")) { // Ensure terminalId is alphabetic
                        TerminalSession terminalSession = null;
                        try {
                            // Deserialize the stored JSON into a TerminalSession object
                            terminalSession = jedisPooled.jsonGet(key, TerminalSession.class);
                            System.out.println("Deserialized TerminalSession: " + terminalSession);
                        } catch (Exception e) {
                            System.out.println("Failed to deserialize key: " + key);
                            e.printStackTrace();
                            continue; // Skip if deserialization fails
                        }

                        if (terminalSession != null && terminalSession.getActive()) { // Changed from isActive() to getActive()
                            System.out.println("Terminal is active: " + terminalId);
                            acceptingTerminals.add(terminalSession);
                        }
                    }
                }

                System.out.println("Total terminals active: " + acceptingTerminals.size());

                // Send a message to each terminal
                for (int i = 0; i < acceptingTerminals.size(); i++) {
                    TerminalSession terminalSession = acceptingTerminals.get(i);
                    String sessionId = terminalSession.getSessionId();
                    WebSocketSession wsSession = sessionMap.get(sessionId);

                    if (wsSession != null && wsSession.isOpen()) {
                        System.out.println("Sending update to terminal: " + terminalSession.getTerminalId());

                        // Create the message using Map<String, String>
                        Map<String, String> updateMessage = new HashMap<>();
                        updateMessage.put("updateTerminal", terminalSession.getTerminalId());
                        updateMessage.put("terminalCount", String.valueOf(acceptingTerminals.size()));
                        updateMessage.put("terminalNumber", String.valueOf(i));

                        // Convert the map to a JSON string using ObjectMapper
                        String jsonMessage = objectMapper.writeValueAsString(updateMessage);

                        // Send the message to the WebSocket session
                        wsSession.sendMessage(new TextMessage(jsonMessage));
                    } else {
                        System.out.println(
                                "WebSocket session is closed or null for terminal: "
                                        + terminalSession.getTerminalId());
                    }
                }
            } catch (Exception e) {
                // Handle exceptions, such as JSON processing or WebSocket issues
                System.out.println("Exception in notifyTerminalsOfActiveConnections: ");
                e.printStackTrace();
            }
        }

        @Transactional
        public void handleInitializeAction(WebSocketSession session, Map<String, Object> payload) throws Exception {

            int merchantId = (int) payload.get("merchantId");
            String terminalId = (String) payload.get("terminalId");

            System.out.println("Received terminalId: " + terminalId);
            System.out.println("Received merchantId: " + merchantId);

            if (terminalId == null || terminalId.isEmpty() || !terminalId.matches("[a-zA-Z]+")) {
                // Send an error response
                sendErrorMessage(session,
                        "Initialization Failed: Invalid terminalId. It must be non-empty and contain only alphabetic characters.");
                return;
            }

            // Create the Redis key
            String redisKey = merchantId + "." + terminalId;

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
                        System.out.println("Closing the connection for..." + terminalId);
                    }
                }

                // Store the new session in Redis and in the session map
                TerminalSession newSession = new TerminalSession(merchantId, terminalId, session.getId(), true);

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
                session.sendMessage(new TextMessage("Initialization successful for terminal " + terminalId));

                // Notify each terminal of active WebSocket connections
                notifyTerminalsOfActiveConnections(merchantId);
                handleRefreshAction(session, payload);

            }
        }

        @Transactional
        public void handleDeliverAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            int customerId = (int) payload.get("customerId");
            String terminalId = (String) payload.get("terminalId");

            String orderRedisKey = merchantId + "." + customerId;

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

                if (!terminalId.equals(currentClaimer)) {
                    sendErrorMessage(session,
                            "You cannot deliver this order because it was claimed by another terminal.");
                    jedis.unwatch(); // Unwatch if the order was claimed by another terminal
                    return;
                }

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
                        "http://34.230.32.169:8080/orders/save",
                        order);

                Map<String, Object> data = new HashMap<>();
                data.put("update", Collections.singletonList(order));

                broadcastToMerchant(merchantId, data);

                orderWebSocketHandler.updateCustomer(order);
            }
        }

        @Transactional
        public void handleCancelAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            int customerId = (int) payload.get("customerId");
            String cancelingTerminalId = (String) payload.get("terminalId");

            String orderRedisKey = merchantId + "." + customerId;

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

                // Check if the canceling terminal is the one who claimed the order
                String currentClaimer = order.getClaimer();
                if (!cancelingTerminalId.equals(currentClaimer)) {
                    sendErrorMessage(session, "You cannot cancel this order as it was claimed by another terminal.");
                    jedis.unwatch(); // Unwatch if the order was claimed by another terminal
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
                restTemplate.postForLocation(
                        "http://34.230.32.169:8080/orders/save",
                        order);

                Map<String, Object> data = new HashMap<>();
                data.put("update", Collections.singletonList(order));

                broadcastToMerchant(merchantId, data);

                orderWebSocketHandler.updateCustomer(order);
            }
        }

        @Transactional
        public void handleClaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            int customerId = (int) payload.get("customerId");
            String claimingTerminalId = (String) payload.get("terminalId");

            String orderRedisKey = merchantId + "." + customerId;

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
                order.setClaimer(claimingTerminalId);

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

                Map<String, Object> data = new HashMap<>();
                data.put("update", Collections.singletonList(order));

                broadcastToMerchant(merchantId, data);

                orderWebSocketHandler.updateCustomer(order);

            }
        }

        @Transactional
        public void handleReadyAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            int customerId = (int) payload.get("customerId");
            String readyTerminalId = (String) payload.get("terminalId");

            String orderRedisKey = merchantId + "." + customerId;

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

                if (!readyTerminalId.equals(currentClaimer)) {
                    sendErrorMessage(session,
                            "You cannot mark this order as ready because it was claimed by another terminal.");
                    jedis.unwatch(); // Unwatch if the order was claimed by another terminal
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

                broadcastToMerchant(merchantId, data);

                orderWebSocketHandler.updateCustomer(order);
            }
        }

        @Transactional
        public void handleUnclaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            int customerId = (int) payload.get("customerId");
            String unclaimingTerminalId = (String) payload.get("terminalId");

            String orderRedisKey = merchantId + "." + customerId;

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

                if (!unclaimingTerminalId.equals(currentClaimer)) {
                    sendErrorMessage(session,
                            "You cannot unclaim this order because it was claimed by another terminal.");
                    jedis.unwatch(); // Unwatch if the order was claimed by another terminal
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

                Map<String, Object> data = new HashMap<>();
                data.put("update", Collections.singletonList(order));

                broadcastToMerchant(merchantId, data);

                orderWebSocketHandler.updateCustomer(order);
            }
        }

        @Transactional
        public void handleDisableAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
            int merchantId = (int) payload.get("merchantId");
            String terminalId = (String) payload.get("terminalId");

            if (terminalId == null || terminalId.isEmpty() || !terminalId.matches("[a-zA-Z]+")) {
                // Send an error response
                sendErrorMessage(session,
                        "Invalid terminalId. It must be non-empty and contain only alphabetic characters.");
                return;
            }

            // Create the Redis key
            String redisKey = merchantId + "." + terminalId;

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.watch(redisKey); // Watch the key for changes

                // Retrieve the JSON object from Redis
                Object terminalJsonObj = jedisPooled.jsonGet(redisKey);

                if (terminalJsonObj == null) {
                    sendErrorMessage(session, "No active session found for terminal " + terminalId + ".");
                    jedis.unwatch(); // Unwatch if the data doesn't exist
                    return;
                }

                // Convert the retrieved Object to a JSON string
                String terminalJson;
                try {
                    terminalJson = objectMapper.writeValueAsString(terminalJsonObj);
                } catch (JsonProcessingException e) {
                    sendErrorMessage(session, "Failed to process session data.");
                    jedis.unwatch(); // Unwatch if processing fails
                    return;
                }

                // Deserialize the JSON string into a Map
                Map<String, Object> existingSessionData;
                try {
                    existingSessionData = objectMapper.readValue(terminalJson, Map.class);
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
                    sendErrorMessage(session, "Failed to disable the terminal due to a conflict. Please try again.");
                    return;
                }

                // Send a success response to the client
                session.sendMessage(new TextMessage("{\"disable\":\"true\"}"));

                // Notify all terminals of active WebSocket connections
                notifyTerminalsOfActiveConnections(merchantId);
            } catch (Exception e) {
                // Handle any exceptions that occur during the process
                e.printStackTrace();
                sendErrorMessage(session, "An error occurred while disabling the terminal. Please try again.");
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

            try (Jedis jedis = jedisPool.getResource()) {
                // Prepare to scan Redis for keys matching the pattern
                ScanParams scanParams = new ScanParams().match(merchantId + ".*");
                String cursor = "0";

                List<Order> orders = new ArrayList<>();

                System.out.println("Scanning Redis with pattern: " + merchantId + ".*");
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

                    // Process each key, but only if it matches the format merchantId.# (where # is a number)
                    for (String key : keys) {
                        // Match the format merchantId.# and ensure the part after the dot is entirely numeric
                        if (key.matches(merchantId + "\\.\\d+")) { // This regex matches merchantId.# where # is one or more digits
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

                String merchantStatus = jedis.hget(String.valueOf(merchantId), "open");

                if (merchantStatus != null) {
                    boolean status = merchantStatus.equals("true");

                    JSONObject openOrClosed = new JSONObject();
                    openOrClosed.put("merchantStatus", status);

                    session.sendMessage(new TextMessage(openOrClosed.toString()));
                    System.out.println("Sent merchant status to session: " + session.getId());
                } else {
                    // Send an error or default status if happy hour status is not found
                    sendErrorMessage(session, "Failed to retrieve merchant status.");
                }

            } catch (Exception e) {
                e.printStackTrace(); // Handle exceptions
                sendErrorMessage(session, "Error retrieving orders");
            }

        }

        public void broadcastToMerchant(int merchantId, Map<String, Object> data) throws IOException {
            // Prepare the data to be broadcasted
            String message = objectMapper.writeValueAsString(data);

            // Debug: Print the message that is being broadcasted
            System.out.println("Broadcasting message to merchant " + merchantId + ": " + message);

            // Step 1: Scan Redis for keys matching the format merchantId.AlphabeticLetter
            String pattern = merchantId + ".[a-zA-Z]*";
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

                        if (terminalSession != null //&& terminalSession.getActive()
                        ) {
                            matchingSessionIds.add(terminalSession.getSessionId());
                        }
                    }
                } while (!"0".equals(cursor));
            }

            // Debug: Print the matching session Ids
            System.out.println("Matching session Ids for merchant " + merchantId + ": " + matchingSessionIds);

            // Step 3: Filter the sessionMap to find open sessions that match the retrieved sessionIds
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