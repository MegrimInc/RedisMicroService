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


}
