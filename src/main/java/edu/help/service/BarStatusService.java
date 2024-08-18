package edu.help.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BarStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, Boolean> hashOperations;

    public BarStatusService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    public void setBarOpenStatus(int barId, boolean isOpen) {
        hashOperations.put("bar:" + barId, "open", isOpen);
    }

    public void setBarHappyHourStatus(int barId, boolean isHappyHour) {
        hashOperations.put("bar:" + barId, "happyHour", isHappyHour);
    }

    public Boolean getBarOpenStatus(int barId) {
        return hashOperations.get("bar:" + barId, "open");
    }

    public Boolean getBarHappyHourStatus(int barId) {
        return hashOperations.get("bar:" + barId, "happyHour");
    }
}
