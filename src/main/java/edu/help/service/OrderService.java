package edu.help.service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.Order;
import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.ResponseWrapper;
import redis.clients.jedis.JedisPooled;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPooled jedis; // Redis client

    public OrderService(RestTemplate restTemplate, LettuceConnectionFactory redisConnectionFactory) {
        this.restTemplate = restTemplate;
        // Initialize JedisPooled client
        this.jedis = new JedisPooled(redisConnectionFactory.getHostName(), redisConnectionFactory.getPort());
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        System.out.println("Processing order for barId: " + orderRequest.getBarId());

        // Check if the key already exists in Redis
        String orderKey = generateOrderKey(orderRequest);
        if (jedis.exists(orderKey)) {
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Order already in progress for barId: " + orderRequest.getBarId()
            ));
            closeSession(session);
            return;
        }

        // Process the order with PostgreSQL
        try {
            OrderResponse orderResponse = restTemplate.postForObject(
                "http://34.230.32.169:8080/" + orderRequest.getBarId() + "/processOrder",
                orderRequest,
                OrderResponse.class
            );

            if (orderResponse != null) {
                if ("Bar is closed".equals(orderResponse.getMessage())) {
                    sendOrderResponse(session, new ResponseWrapper(
                        "error",
                        null,
                        "Bar is closed. Please try again later."
                    ));
                    closeSession(session);
                    return;
                }

                // Create Order object
                Order order = new Order(
                    orderRequest.getBarId(),
                    orderRequest.getUserId(),
                    orderResponse.getTotalPrice(),
                    convertDrinksToOrders(orderResponse.getDrinks()), // Use updated method
                    "unready",
                    "", // Claimer could be set based on further logic
                    getCurrentTimestamp() // Use updated method
                );

                // Serialize the order to JSON
          
            
           

                jedis.jsonSetWithEscape(orderKey, order);
                System.out.println("Stored order in Redis with key: " + orderKey);
                
            

                sendOrder(order, session);
            }
        } catch (RestClientException e) {
            closeSession(session);
            e.printStackTrace();
            sendOrderResponse(session, new ResponseWrapper(
                "error",
                null,
                "Failed to process order: No response from PostgreSQL."
            ));
        } 
    }

    private List<Order.DrinkOrder> convertDrinksToOrders(List<OrderResponse.DrinkOrder> drinkResponses) {
        // Convert drink orders to a list of Order.DrinkOrder objects with id, names, and quantities
        return drinkResponses.stream()
                             .map(drink -> new Order.DrinkOrder(drink.getDrinkId(), drink.getDrinkName(), drink.getQuantity()))
                             .toList();
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendOrderResponse(WebSocketSession session, ResponseWrapper responseWrapper) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(responseWrapper);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendOrder(Order order, WebSocketSession session) {
        try {
            ResponseWrapper responseWrapper = new ResponseWrapper(
                "success",
                order,
                "Order processed successfully."
            );
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
                message
            );
            sendOrderResponse(session, responseWrapper);
            if (session.isOpen()) {
                session.close();
                System.out.println("WebSocket session closed due to error.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
                System.out.println("WebSocket session closed.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTimestamp() {
        // Get current timestamp in milliseconds with date and time formatting
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(formatter);
    }
}
