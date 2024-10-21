package edu.help.websocket;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.dto.ResponseWrapper;
import edu.help.service.OrderService;



@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private static OrderWebSocketHandler instance;

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> deviceTokenMap = new ConcurrentHashMap<>();
    private final ApnsClient apnsClient;
   

    public OrderWebSocketHandler(OrderService orderService)
            throws InvalidKeyException, SSLException, NoSuchAlgorithmException, IOException {
        this.orderService = orderService;

        this.apnsClient = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST) // Use `PRODUCTION_APNS_HOST` for development
                .setSigningKey(ApnsSigningKey.loadFromPkcs8File(
                        new File("/app/AuthKey_4TSCNPNRJC.p8"), // Replace with the path to your .p8 file
                        "6TK33N3VRX",
                        "4TSCNPNRJC"))
                .build();

        // this.apnsClient = new ApnsClientBuilder()
        //         .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST) // Use `DEVELOPMENT_APNS_HOST` for debugging
        //         .setSigningKey(ApnsSigningKey.loadFromPkcs8File(
        //                 new File("/app/AuthKey_4TSCNPNRJC.p8"), // Replace with the path to your .p8 file     
        //                 "6TK33N3VRX",
        //                 "4TSCNPNRJC"))
        //         .build();

        // Configure ObjectMapper to ignore unknown fields
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        instance = this;

    }

    // Getter for the singleton instance
    public static OrderWebSocketHandler getInstance() {
        return instance;
    }

    //Method to handle a successful connection
        @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Log the connection establishment
        System.out.println("WebSocket connection established with session ID: " + session.getId());

        sessionMap.put(session.getId(), session);

        // Create a ResponseWrapper object with the desired message and messageType
        ResponseWrapper response = new ResponseWrapper(
            "ping",                          // messageType
            null,                             // data
            "Successfully connected to server" // message
        );

        // Send the response to the client
        sendOrderResponse(session, response);

        System.out.println("Sent ping message to " + session.getId());
    }

    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Log the received message payload
            System.out.println("WebSocket message received: " + message.getPayload());

            // Parse the payload into a Map to extract action and OrderRequest
            Map<String, Object> payloadMap = parsePayload(message.getPayload());

            String action = (String) payloadMap.get("action");
            payloadMap.remove("action");

            OrderRequest orderRequest = objectMapper.convertValue(payloadMap, new TypeReference<OrderRequest>() {
            });

            System.out.println("Action: " + action);
            System.out.println("Parsed OrderRequest: " + orderRequest);

            // Handle the action based on its value
            switch (action.toLowerCase()) {
                case "create":
                    if (orderRequest != null) {
                        orderService.processOrder(orderRequest, session);
                    } else {
                        sendErrorResponse(session, "Invalid order format.");
                    }

                    break;
                case "delete":
                    if (orderRequest != null) {
                        orderService.cancelOrderIfUnclaimed(orderRequest, session); // Pass full OrderRequest
                        
                    } else {
                    sendErrorResponse(session, "Invalid order format.");
                    }
                    
                    break;
                case "refresh":
                    int userIdToRefresh = (int) payloadMap.get("userId");
                    String deviceToken = (String) payloadMap.get("deviceToken");
                    updateDeviceToken(userIdToRefresh, deviceToken);
                    orderService.refreshOrdersForUser(userIdToRefresh, session);

                    break;
                default:
                    sendErrorResponse(session, "Invalid action.");
                    break;
            }

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            session.sendMessage(new TextMessage("Error: " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) {
        System.out.println("Received payload: " + payload); // Log the raw payload

        try {
            // Parse payload as a Map to extract action and OrderRequest
            Map<String, Object> result = objectMapper.readValue(payload, Map.class);
            System.out.println("Parsed result: " + result); // Log the parsed result
            return result;
        } catch (IOException e) {
            System.err.println("Failed to parse payload: " + e.getMessage()); // Log the exception
            e.printStackTrace(); // Print the stack trace for more detailed debugging information
            return null;
        }
    }

    private void sendErrorResponse(WebSocketSession session, String error) {
        try {
            session.sendMessage(new TextMessage(error));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendOrderResponse(WebSocketSession session, ResponseWrapper responseWrapper) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(responseWrapper);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUser(Map<String, Object> orderData) throws IOException {
        String status = (String) orderData.get("status"); // Extract status from orderData
        String claimer = (String) orderData.get("claimer"); // Extract claimer, default to empty string
        String sessionId = (String) orderData.get("sessionId");
        int userId = (int) orderData.get("userId");
        String deviceToken = deviceTokenMap.get(String.valueOf(userId));

        // Retrieve the session from the sessionMap using the sessionId
        WebSocketSession userSession = sessionMap.get(sessionId);

        // If the session is found and open, send the message
        if (userSession != null && userSession.isOpen()) {
            // Create a ResponseWrapper with the update information
            ResponseWrapper response = new ResponseWrapper(
                    "update", // messageType
                    orderData, // data to send
                    "Order update successful." // message
            );

            // Convert the ResponseWrapper to a JSON string
            String jsonResponse = objectMapper.writeValueAsString(response);

            // Send the response to the user
            userSession.sendMessage(new TextMessage(jsonResponse));
        } else {
            System.err.println("User session not found or closed: " + sessionId);
        }

        // Check if the deviceToken is not null and not empty
        if (deviceToken != null && !deviceToken.isEmpty()) {
            // Construct the notification message based on the order status and claimer
            String notificationMessage;
            switch (status.toLowerCase()) {
                case "unready":
                    notificationMessage = claimer.isEmpty()
                            ? "Your order has been unclaimed."
                            : "Station \"" + claimer + "\" has claimed your order!";
                    break;
                case "ready":
                    notificationMessage = "Your order is now ready at station \"" + claimer + "\".";
                    break;
                case "delivered":
                    notificationMessage = "Station \"" + claimer + "\" has delivered your order.";
                    break;
                case "canceled":
                    notificationMessage = "Station \"" + claimer + "\" has canceled your order.";
                    break;
                default:
                    System.err.println("Unknown order status: " + status);
                    return; // Exit early if status is not recognized
            }

            // Send the push notification with the constructed message
            System.out.println("Sending push notification: " + notificationMessage);
            sendPushNotification(deviceToken, notificationMessage);
        } else {
            System.err.println("No device token found for userId: " + userId + ", unable to send push notification.");
        }

    }

    // Method to update the deviceTokenMap from OrderService
    public void updateDeviceToken(int userId, String deviceToken) {
        String userIdStr = String.valueOf(userId);
        deviceTokenMap.put(userIdStr, deviceToken);
        System.out.println("Device token for userId " + userId + " has been stored/updated.");
    }

    // Corrected sendPushNotification method
    public void sendPushNotification(String deviceToken, String alertMessage) {
        try {
            // Use the correct payload builder
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertBody(alertMessage);
            String payload = payloadBuilder.build();

            // Sanitize the device token (removes spaces and special characters)
            String sanitizedToken = TokenUtil.sanitizeTokenString(deviceToken);

            // Create the push notification
            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                    sanitizedToken,
                    "com.example.barzzyApp9", // Replace with your app's bundle ID
                    payload);

            // Use PushNotificationFuture for handling notification responses
            PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture = this.apnsClient
                    .sendNotification(pushNotification);

            // Handle response asynchronously
            sendNotificationFuture.whenComplete((response, cause) -> {
                if (response != null) {
                    if (response.isAccepted()) {
                        System.out.println("Push notification accepted by APNs gateway.");
                    } else {
                        System.out.println("Notification rejected by the APNs gateway: " +
                                response.getRejectionReason());
                    }
                } else {
                    System.err.println("Failed to send push notification.");
                    cause.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error sending push notification: " + e.getMessage());
        }
    }

    public void sendCreateNotification(OrderRequest orderRequest) {
        // Calculate total quantity for points
        int totalQuantity = orderRequest.getDrinks().stream()
                .mapToInt(OrderRequest.DrinkOrder::getQuantity)
                .sum();
        String message = "Order placed! " + (totalQuantity * 75) + 
                         " pts have been awarded to your account!";
    
        // Retrieve device token
        String deviceToken = deviceTokenMap.get(String.valueOf(orderRequest.getUserId()));
        if (deviceToken != null && !deviceToken.isEmpty()) {
            sendPushNotification(deviceToken, message);
        } else {
            System.err.println("No device token found for userId: " + orderRequest.getUserId());
        }
    }
    
    public void sendCancelNotification(OrderRequest orderRequest) {
            String message = "Order canceled. All points have been refunded";
    
            // Retrieve device token
            String deviceToken = deviceTokenMap.get(String.valueOf(orderRequest.getUserId()));
            if (deviceToken != null && !deviceToken.isEmpty()) {
                sendPushNotification(deviceToken, message);
            } else {
                System.err.println("No device token found for userId: " + orderRequest.getUserId());
            }
        
    }
}