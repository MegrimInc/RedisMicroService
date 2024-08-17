package edu.help.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.model.OrderRequest;
import edu.help.model.OrderResponse;
import edu.help.model.OrderResponse.DrinkOrder;

@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisService(RedisTemplate<String, Object> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    public void processOrder(OrderRequest orderRequest, WebSocketSession session) {
        // Existing code...
    }

    public void set(String key, String jsonValue) {
        redisTemplate.opsForValue().set(key, jsonValue);
    }

    public void storeSession(String barId, String bartenderID, WebSocketSession session) {
        try {
            Map<String, Object> valueMap = new HashMap<>();
            valueMap.put("sessionId", session.getId()); // Store session ID or other relevant info
            valueMap.put("active", true); // Boolean value

            String redisKey = barId + "." + bartenderID;
            String redisValueJson = objectMapper.writeValueAsString(valueMap);

            set(redisKey, redisValueJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateOrderKey(OrderRequest orderRequest) {
        return String.format("%d.%d", orderRequest.getBarId(), orderRequest.getUserId());
    }

    private void sendResponse(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
