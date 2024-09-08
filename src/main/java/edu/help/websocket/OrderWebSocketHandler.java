package edu.help.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.dto.ResponseWrapper;
import edu.help.service.OrderService;

@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    



   
    public OrderWebSocketHandler(OrderService orderService ) {
        this.orderService = orderService;
       
    }

    // Method to handle a successful connection
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
            //OrderRequest orderRequest = objectMapper.convertValue(payloadMap.get("orderRequest"), OrderRequest.class);

            OrderRequest orderRequest = objectMapper.convertValue(payloadMap, new TypeReference<OrderRequest>() {});

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
                    int barId = (int) payloadMap.get("barId");
                    int userId = (int) payloadMap.get("userId");
                    orderService.cancelOrderIfUnclaimed(barId, userId, session);
                    
                    break;
                case "refresh":

                int userIdToRefresh = (int) payloadMap.get("userId");
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
        System.out.println("Received payload: " + payload);  // Log the raw payload
        
        try {
            // Parse payload as a Map to extract action and OrderRequest
            Map<String, Object> result = objectMapper.readValue(payload, Map.class);
            System.out.println("Parsed result: " + result);  // Log the parsed result
            return result;
        } catch (IOException e) {
            System.err.println("Failed to parse payload: " + e.getMessage());  // Log the exception
            e.printStackTrace();  // Print the stack trace for more detailed debugging information
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
        String sessionId = (String) orderData.get("sessionId");
    
        // Retrieve the session from the sessionMap using the sessionId
        WebSocketSession userSession = sessionMap.get(sessionId);


    
        // If the session is found and open, send the message
        if (userSession != null && userSession.isOpen()) {
            // Create a ResponseWrapper with the update information
            ResponseWrapper response = new ResponseWrapper(
                "update",            // messageType
                orderData,           // data to send
                "Order update successful."  // message
            );

            
            // Convert the ResponseWrapper to a JSON string
            String jsonResponse = objectMapper.writeValueAsString(response);
            
            // Send the response to the user
            userSession.sendMessage(new TextMessage(jsonResponse));
        } else {
            System.err.println("User session not found or closed: " + sessionId);
        }
    }
}