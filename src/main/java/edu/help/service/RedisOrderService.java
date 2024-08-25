package edu.help.service;



import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import edu.help.dto.Order;

@Service
public class RedisOrderService {

    private final RestTemplate restTemplate;

    public RedisOrderService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendOrderToPostgres(Order orderToSave) {
        String url = "http://34.230.32.169:8080/hierarchy/save";  // Replace <Postgres-Service-IP> with the actual IP address or hostname
        restTemplate.postForObject(url, orderToSave, String.class);
    }
}
