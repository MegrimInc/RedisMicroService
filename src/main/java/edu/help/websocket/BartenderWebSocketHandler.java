package edu.help.websocket;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import edu.help.dto.*;
import jakarta.mail.internet.MimeMessage;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.format.DateTimeFormatter;

import org.springframework.mail.javamail.JavaMailSenderImpl;

import org.json.JSONObject;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    public BartenderWebSocketHandler(JedisPooled jedisPooled, JedisPool jedisPool,
            OrderWebSocketHandler orderWebSocketHandler, RestTemplate restTemplate) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
        this.orderWebSocketHandler = orderWebSocketHandler;
        instance = this;
        this.restTemplate = restTemplate;

    }


    public static BartenderWebSocketHandler getInstance() {
        return instance;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Log the received message payload
            String payload = message.getPayload();
            System.out.println("Bartender WebSocket message received: " + payload);


            // Parse the JSON message
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);

            // Extract the action from the payload
            String action = (String) payloadMap.get("action");

            // Handle the action based on its value
            System.out.println("Action received:" + action);
            switch (action) {

                case "Claim Tips":
                    handleClaimTips(session, payloadMap);
                    break;

                case "initialize":
                    handleInitializeAction(session, payloadMap);
                    break;

                case "happyHour":
                    int barID3 = (int) payloadMap.get("barID");
                    String barKey3 = String.valueOf(barID3);  // Adjust this to match your specific key format

                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.watch(barKey3);

                        // Check if happy hour is already active
                        boolean isHappyHourActive = Boolean.parseBoolean(jedis.hget(barKey3, "happyHour"));
                        if (isHappyHourActive) {
                            sendErrorMessage(session, "Happy Hour is already active.");
                            jedis.unwatch();
                            break;
                        }

                        Transaction transaction = jedis.multi();
                        transaction.hset(barKey3, "happyHour", "true");
                        // Include any other fields that need to be set during happy hour activation
                        List<Object> results = transaction.exec();

                        if (results != null) {
                            // Notify all bartenders if the transaction was successful
                            Map<String, Object> happyHourPayload = new HashMap<>();
                            happyHourPayload.put("happyHour", true);

                            // Use the existing broadcastToBar method to notify all bartenders
                            broadcastToBar(barID3, happyHourPayload);
                        } else {
                            sendErrorMessage(session, "Failed to start Happy Hour due to a race condition.");
                        }

                    } catch (Exception e) {
                        sendErrorMessage(session, "An error occurred while starting Happy Hour.");
                    }
                    break;

                case "sadHour":
                    int barID4 = (int) payloadMap.get("barID");
                    String barKey4 = String.valueOf(barID4);  // Adjust this to match your specific key format

                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.watch(barKey4);

                        // Check if happy hour is already inactive
                        boolean isHappyHourInactive = !Boolean.parseBoolean(jedis.hget(barKey4, "happyHour"));
                        if (isHappyHourInactive) {
                            sendErrorMessage(session, "Happy Hour is already inactive.");
                            jedis.unwatch();
                            break;
                        }

                        Transaction transaction = jedis.multi();
                        transaction.hset(barKey4, "happyHour", "false");
                        // Include any other fields that need to be set during sad hour activation
                        List<Object> results = transaction.exec();

                        if (results != null) {
                            // Notify all bartenders if the transaction was successful
                            Map<String, Object> sadHourPayload = new HashMap<>();
                            sadHourPayload.put("happyHour", false);

                            // Use the existing broadcastToBar method to notify all bartenders
                            broadcastToBar(barID4, sadHourPayload);
                        } else {
                            sendErrorMessage(session, "Failed to end Happy Hour due to a race condition.");
                        }

                    } catch (Exception e) {
                        sendErrorMessage(session, "An error occurred while ending Happy Hour.");
                    }
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
                    int barID = (int) payloadMap.get("barID");
                    String barKey = String.valueOf(barID);  // Adjust this to match your specific key format

                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.watch(barKey);

                        // Check if the bar is already open
                        boolean isBarOpen = Boolean.parseBoolean(jedis.hget(barKey, "open"));
                        if (isBarOpen) {
                            sendErrorMessage(session, "Bar is already open.");
                            jedis.unwatch();
                            break;
                        }

                        Transaction transaction = jedis.multi();
                        transaction.hset(barKey, "open", "true");
                        // Include any other fields that need to be set during opening
                        List<Object> results = transaction.exec();

                        if (results != null) {
                            // Notify all bartenders if the transaction was successful
                            Map<String, Object> openPayload = new HashMap<>();
                            openPayload.put("barStatus", true);

                            // Convert the map to a JSON string (if needed)

                            // Use the existing broadcastToBar method to notify all bartenders
                            broadcastToBar(barID, openPayload);
                        } else {
                            sendErrorMessage(session, "Failed to open the bar due to a race condition.");
                        }

                    } catch (Exception e) {
                        sendErrorMessage(session, "An error occurred while opening the bar.");
                    }
                    break;

                case "close":
                    int barID2 = (int) payloadMap.get("barID");
                    String barKey2 = String.valueOf(barID2);  // Adjust this to match your specific key format

                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.watch(barKey2);

                        // Check if the bar is already closed
                        boolean isBarClosed = !Boolean.parseBoolean(jedis.hget(barKey2, "open"));
                        if (isBarClosed) {
                            sendErrorMessage(session, "Bar is already closed.");
                            jedis.unwatch();
                            break;
                        }

                        Transaction transaction = jedis.multi();
                        transaction.hset(barKey2, "open", "false");
                        // Include any other fields that need to be set during closing
                        List<Object> results = transaction.exec();

                        if (results != null) {
                            // Notify all bartenders if the transaction was successful
                            Map<String, Object> closePayload = new HashMap<>();
                            closePayload.put("barStatus", false);

                            // Use the existing broadcastToBar method to notify all bartenders
                            broadcastToBar(barID2, closePayload);
                        } else {
                            sendErrorMessage(session, "Failed to close the bar due to a race condition.");
                        }

                    } catch (Exception e) {
                        sendErrorMessage(session, "An error occurred while closing the bar.");
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


    private void handleClaimTips(WebSocketSession session, Map<String, Object> payload) {
        System.out.println("Handling tips...");
        try {
            // Extract payload information
            int barID = (int) payload.get("barID");
            String bartenderName = (String) payload.get("name");
            String bartenderEmail = (String) payload.get("email"); // equal to "" if no alternateEmail
            String bartenderID = (String) payload.get("bartenderID"); // equal to "" if no alternateEmail

            // Get current date and format it
            ZonedDateTime now = ZonedDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a z");
            String formattedDate = now.format(formatter);
            long dateClaimed = now.toInstant().toEpochMilli();  // Milliseconds timestamp for dateClaimed

            String digitalSignature;
            String tempPayload = "";
            String emailReturnPayload = "";
            String plaintextReturnPayload = "";
            String returnPayloadJSON = "";




            // Start Saarthak's section

            List<PostgresOrder> tipsList = new ArrayList<PostgresOrder>(); //new data object to correspond to what postgres sends.
            String barEmail = "temp@diepio.org";

            try {
                // Create the TipClaimRequest object
                TipClaimRequest tipClaimRequest = new TipClaimRequest(barID, bartenderName, bartenderEmail, bartenderID);

                // Hit the claim tips endpoint
                String url = "http://34.230.32.169:8080/orders/claim";
                TipClaimResponse response = restTemplate.postForObject(url, tipClaimRequest, TipClaimResponse.class);

                // Extract tipsList and barEmail from the response
                if (response != null) {
                    tipsList = response.getOrders(); // Orders list
                    barEmail = response.getBarEmail(); // Bar email
                } else {
                    throw new RuntimeException("No response from claim tips endpoint.");
                }

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error while processing tips claim.", e);
            }
// End Saarthak's section

            /**
             * TODO: WESLEY
             *
             * TipsList and barEmail is extracted from PG sucesfully.
             * There is 1 potential issue.
             * The orderLIst that I give back represents the order entity in postgres, not the order in redis.
             * I have created a new class, PostgresOrder.java, that represents the orders that will be sent back to you.
             * Please change the below (and the helper methods) accordingly, if necessary.
             * I don't think much change will be required since PostgresOrder.java has all the info that you need anyways.
             *
             * SAARTHAK: just did this.
             */

            // Serialize the data for tempPayload (without digital signature)
            Map<String, Object> tempPayloadData = Map.of(
                    "barID", barID,
                    "bartenderID", bartenderID,
                    "bartenderName", bartenderName,
                    "orders", tipsList,
                    "dateClaimed", dateClaimed
            );
            tempPayload = new ObjectMapper().writeValueAsString(tempPayloadData);
            plaintextReturnPayload = tempPayload.replace('\n', ' ');

            // Generate digital signature
            digitalSignature = generateDigitalSignature(plaintextReturnPayload);

            // Prepare the return payload JSON, matching the frontend structure
            Map<String, Object> returnPayloadData = new HashMap<>(tempPayloadData);
            returnPayloadData.put("digitalSignature", digitalSignature);
            returnPayloadJSON = new ObjectMapper().writeValueAsString(returnPayloadData);

            // Prepare the email payload content with HTML formatting
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("<h1 style='text-align:center;'>Tip Report</h1>")
                    .append("<h2 style='text-align:center;'>Bar ID: ").append(barID).append("</h2>")
                    .append("<p style='text-align:center; font-weight:bold;'>Bartender ID: <span style='color:blue;'>")
                    .append(bartenderID).append("</span> | Bartender Name: <span style='color:blue;'>")
                    .append(bartenderName).append("</span></p>")
                    .append("<p style='text-align:center;'>Claimed at: <span style='color:green;'>")
                    .append(formattedDate).append("</span></p>")
                    .append("<h3 style='text-align:center; color:darkgreen;'>Total Tip Amount: <span style='color:green;'>$")
                    .append(calculateTotalTipAmount(tipsList)).append("</span></h3>");

            emailContent.append("<h3>Order Tips:</h3><ul>");
            for (PostgresOrder order : tipsList) {
                ZonedDateTime orderDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(String.valueOf(order.getTimestamp()))), now.getZone());
                String orderFormattedDate = orderDate.format(formatter);
                emailContent.append("<li><strong>Order ID#</strong> ").append(order.getUserId())
                        .append(": $").append(order.getTip())
                        .append(" | ").append(orderFormattedDate).append("</li>");
            }
            emailContent.append("</ul>");

            emailContent.append("<br><p><strong>Plaintext:</strong><br>")
                    .append("<code>").append(plaintextReturnPayload).append("</code></p>")
                    .append("<p><strong>Server Signature:</strong><br>")
                    .append("<code>").append(digitalSignature).append("</code></p>");

            // Send emails to the bar and bartender
            String subject = "Tip Receipt for " + bartenderName + " (Bar #" + barID + ")";
            try {
                sendTipEmail(barEmail, subject, emailContent.toString());
                if (!bartenderEmail.isEmpty())  sendTipEmail(bartenderEmail, subject, emailContent.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


            // Send success message with return payload JSON to the frontend
            String trimmedPayload = returnPayloadJSON.substring(1, returnPayloadJSON.length() - 1);

            session.sendMessage(new TextMessage("{\"Tip Claim Successful\":\"Tip Claim Successful\", " + trimmedPayload + "}"));

        } catch (IOException e) {
            try {
                session.sendMessage(new TextMessage("{\"Tip Claim Failed\":\"Tip Claim Failed\"}"));
                sendErrorMessage(session, "Tip claim processing failed.");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }


    private double calculateTotalTipAmount(List<PostgresOrder> tipsList) {
        double totalTipAmount = 0.0;
        for (PostgresOrder order : tipsList) {
            totalTipAmount += order.getTip();
        }
        return totalTipAmount;
    }

    private void sendTipEmail(String email, String subject, String content) {
        JavaMailSenderImpl test = new JavaMailSenderImpl();
        test.setHost("email-smtp.us-east-1.amazonaws.com");
        test.setPort(587);

        test.setUsername("AKIARKMXJUVKGK3ZC6FH");
        test.setPassword("BJ0EwGiCXsXWcZT2QSI5eR+5yFzbimTnquszEXPaEXsd");

        Properties props = test.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "true");

        try {
            // Create a MimeMessage
            MimeMessage message = test.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Set the basic email attributes
            helper.setTo(email);
            helper.setFrom("noreply@barzzy.site");
            helper.setSubject(subject);

            // Set the email content as plain text
            helper.setText(content, true);

            // Send the email
            test.send(message);
            System.out.println("Successful send");
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }



    private String generateDigitalSignature(String plaintextReturnPayload) {

        try {
            System.out.println("attempting sign");
            return signText(plaintextReturnPayload, getPrivateKey());
        } catch (Exception e) {
            return "Signature Failed. Contact barzzy.llc@gmail.com immediately | " + java.time.Instant.now();
        }
    }

    // Load private key from input or file
    private static PrivateKey getPrivateKey() throws Exception {

        String keyContent = "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";


        keyContent = keyContent.replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "").replaceAll("\n", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    // Sign text
    private static String signText(String text, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(text.getBytes());
        byte[] digitalSignature = signature.sign();
        return Base64.getEncoder().encodeToString(digitalSignature);
    }


    private void notifyBartendersOfActiveConnections(int barID) {
        System.out.println("notifyBartendersOfActiveConnections triggered with barID: " + barID);

        String pattern = barID + ".[a-zA-Z]*";
        System.out.println("Searching for keys with pattern ID: " + pattern);

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
    public void handleInitializeAction(WebSocketSession session, Map<String, Object> payload) throws Exception {

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
    public void handleDeliverAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
    int barID = (int) payload.get("barID");
    int userID = (int) payload.get("userID");
    String bartenderID = (String) payload.get("bartenderID");

    String orderRedisKey = barID + "." + userID;

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
                order
            );

        // Broadcast the updated order to all bartenders
        Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
        broadcastToBar(barID, orderData);

        orderWebSocketHandler.updateUser(orderData);
    }
}


@Transactional
public void handleCancelAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
    int barID = (int) payload.get("barID");
    int userID = (int) payload.get("userID");
    String cancelingBartenderID = (String) payload.get("bartenderID");

    String orderRedisKey = barID + "." + userID;

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
        restTemplate.postForLocation(
                "http://34.230.32.169:8080/orders/save",
                order
            );

        // Broadcast the updated order to all bartenders
        Map<String, Object> orderData = objectMapper.convertValue(order, Map.class);
        broadcastToBar(barID, orderData);

        orderWebSocketHandler.updateUser(orderData);
    }
}


    @Transactional
    public void handleClaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int userID = (int) payload.get("userID");
        String claimingBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + userID;

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
    public void handleReadyAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int userID = (int) payload.get("userID");
        String readyBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + userID;

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
    public void handleUnclaimAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
        int barID = (int) payload.get("barID");
        int userID = (int) payload.get("userID");
        String unclaimingBartenderID = (String) payload.get("bartenderID");

        String orderRedisKey = barID + "." + userID;

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
    public void handleDisableAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
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
    public void handleRefreshAction(WebSocketSession session, Map<String, Object> payload) throws Exception {
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
                                System.out.println("Skipping session-related data for key ID: " + key);
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

            String barStatus = jedis.hget(String.valueOf(barID), "open");

            if (barStatus != null) {
                boolean barStatus1 = barStatus.equals("true");

                JSONObject happyHourMessage = new JSONObject();
                happyHourMessage.put("barStatus", barStatus1);

                session.sendMessage(new TextMessage(happyHourMessage.toString()));
                System.out.println("Sent bar status to session: " + session.getId());
            } else {
                // Send an error or default status if happy hour status is not found
                sendErrorMessage(session, "Failed to retrieve bar status.");
            }

            String happyHourStatus = jedis.hget(String.valueOf(barID), "happyHour");

            if (happyHourStatus != null) {
                boolean isHappyHour = happyHourStatus.equals("true");

                JSONObject happyHourMessage = new JSONObject();
                happyHourMessage.put("happyHour", isHappyHour);

                session.sendMessage(new TextMessage(happyHourMessage.toString()));
                System.out.println("Sent happy hour status to session: " + session.getId());
            } else {
                // Send an error or default status if happy hour status is not found
                sendErrorMessage(session, "Failed to retrieve happy hour status.");
            }



        } catch (Exception e) {
            e.printStackTrace(); // Handle exceptions
            sendErrorMessage(session, "Error retrieving orders");
        }



    }

    public void broadcastToBar(int barID, Map<String, Object> data) throws IOException {
        // Prepare the data to be broadcasted
        String message = objectMapper.writeValueAsString(data);
    
        // Debug: Print the message that is being broadcasted
        System.out.println("Broadcasting message to bar " + barID + ": " + message);
    
        // Step 1: Scan Redis for keys matching the format barId.AlphabeticLetter
        String pattern = barID + ".[a-zA-Z]*";
        List<String> matchingSessionIds = new ArrayList<>();
    
        try (Jedis jedis = jedisPool.getResource()) {
            ScanParams scanParams = new ScanParams().match(pattern);
            String cursor = "0";
    
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
    
                for (String key : scanResult.getResult()) {
                    // Step 2: Retrieve the BartenderSession object from Redis
                    BartenderSession bartenderSession = jedisPooled.jsonGet(key, BartenderSession.class);
    
                    if (bartenderSession != null //&& bartenderSession.getActive()
                    ) {
                        matchingSessionIds.add(bartenderSession.getSessionId());
                    }
                }
            } while (!"0".equals(cursor));
        }
    
        // Debug: Print the matching session IDs
        System.out.println("Matching session IDs for bar " + barID + ": " + matchingSessionIds);
    
        // Step 3: Filter the sessionMap to find open sessions that match the retrieved sessionIds
        for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
            String sessionID = entry.getKey();
            WebSocketSession wsSession = entry.getValue();
    
            if (matchingSessionIds.contains(sessionID) && wsSession.isOpen()) {
                // Debug: Print the session ID before sending the message
                System.out.println("Sending message to session ID: " + sessionID);
                wsSession.sendMessage(new TextMessage(message));
            } else {
                // Debug: If session is not open or does not match, log it
                System.out.println("Session ID: " + sessionID + " is not open or does not match. Skipping.");
            }
        }
    }
    
   

}