package edu.help.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UpdateService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateService(RedisTemplate<String, Object> redisTemplate, RestTemplate restTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    public void set(String key, String jsonValue) {
        redisTemplate.opsForValue().set(key, jsonValue);
    }

    public void storeSession(String barId, String bartenderID, WebSocketSession session) {
        try {
            Map<String, Object> valueMap = new HashMap<>();
            valueMap.put("sessionId", session.getId());
            valueMap.put("active", true);

            String redisKey = barId + "." + bartenderID;
            String redisValueJson = objectMapper.writeValueAsString(valueMap);

            set(redisKey, redisValueJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
