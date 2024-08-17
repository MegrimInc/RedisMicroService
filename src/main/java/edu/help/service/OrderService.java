package edu.help.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.OrderRequest;
import edu.help.dto.OrderResponse;
import edu.help.dto.OrderResponse.DrinkOrder;
import redis.clients.jedis.JedisPooled;

@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JedisPooled jedis; // RedisJSON and RedisTimeSeries client

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
            sendResponse(session, new OrderResponse(
                "Order already in progress for barId: " + orderRequest.getBarId(),
                0.0,
                null,
                ""
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
                    sendResponse(session, new OrderResponse(
                        "Bar is closed. Please try again later.",
                        0.0,
                        null,
                        ""
                    ));
                    closeSession(session);  
                    return;
                }

                // Prepare and store the data in Redis as JSON
                Map<String, Object> orderData = new HashMap<>();
                orderData.put("status", "unready");
                orderData.put("user_id", orderRequest.getUserId());

                // Formatting the drinks as a map of drinkId -> drinkName,quantity
                Map<String, String> drinksMap = new HashMap<>();
                for (DrinkOrder drink : orderResponse.getDrinks()) {
                    drinksMap.put(String.valueOf(drink.getDrinkId()), drink.getDrinkName() + "," + drink.getQuantity());
                }
                orderData.put("drinks", drinksMap);
                orderData.put("total_price", orderResponse.getTotalPrice());
                orderData.put("timestamp", getCurrentTimestamp());

                System.out.println("Order data to be stored in Redis: " + objectMapper.writeValueAsString(orderData));

                // Store the order in Redis as JSON
                jedis.jsonSet(orderKey, objectMapper.writeValueAsString(orderData));
                System.out.println("Stored order in Redis with key: " + orderKey);


                sendResponse(session, new OrderResponse(
                    "Order processed: " + orderResponse.getMessage(),
                    orderResponse.getTotalPrice(),
                    orderResponse.getDrinks(),
                    "success"
                ));
            }
        } catch (RestClientException | IOException e) {
            closeSession(session);  
            e.printStackTrace();
            sendErrorResponse(session, "Failed to process order: No response from PostgreSQL.");
        }
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendResponse(WebSocketSession session, OrderResponse orderResponse) {
        try {
            String jsonResponse = objectMapper.writeValueAsString(orderResponse);
            session.sendMessage(new TextMessage(jsonResponse));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendErrorResponse(WebSocketSession session, String message) {
        try {
            sendResponse(session, new OrderResponse(
                message,
                0.0,
                null,
                "error"
            ));
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

   

    
}
