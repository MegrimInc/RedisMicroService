package edu.help.websocket;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.service.RedisService;

@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderWebSocketHandler(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    try {
        // Log the received message payload
        System.out.println("WebSocket message received: " + message.getPayload());
        
        // Parse the message into an OrderRequest object
        OrderRequest orderRequest = parseOrderRequest(message.getPayload());
        System.out.println("Parsed OrderRequest: " + orderRequest);

        if (orderRequest != null) {
            // Pass the order to RedisService for processing
            
            redisService.processOrder(orderRequest, session);
            

        } else {
            sendErrorResponse(session, "Invalid order format.");
        }

    } catch (Exception e) {
        e.printStackTrace();
        session.sendMessage(new TextMessage("Error: " + e.getMessage()));
    }
}

    

    private OrderRequest parseOrderRequest(String payload) {
        try {
            return objectMapper.readValue(payload, OrderRequest.class);
        } catch (IOException e) {
            e.printStackTrace();
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
}
