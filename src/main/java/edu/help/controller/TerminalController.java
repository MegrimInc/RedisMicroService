package edu.help.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.dto.TerminalSession;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

// Could also be @RestController, which automatically adds @ResponseBody
@RestController
@RequestMapping("redis-test-http")
public class TerminalController {

    private final JedisPooled jedisPooled;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    @Autowired
    public TerminalController(JedisPooled jedisPooled, JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    /**
     * Example GET endpoint:  https://www.megrim.com/ws/http/checkTerminals?merchantId=123
     *
     * Returns a string containing the list of active terminals, e.g. "AB", or "" if none.
     */
    @GetMapping("/checkTerminals")
    public ResponseEntity<String> checkTerminalsGET(@RequestParam("merchantId") int merchantId) {
        try {
            // We'll reuse a method below that does the actual check
            String activeTerminals = getActiveTerminals(merchantId);
            return ResponseEntity.ok(activeTerminals);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error checking terminals: " + e.getMessage());
        }
    }


    /**
     * The main logic that scans Redis for <merchantId>.<terminalId> keys,
     * deserializes them into TerminalSession objects, and
     * collects the ones that are 'active'.
     */
    private String getActiveTerminals(int merchantId) throws Exception {
        // Example pattern: "123.[a-zA-Z]*" for merchantId=123
        String pattern = merchantId + ".[a-zA-Z]*";
        List<String> activeTerminals = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            // We'll scan Redis for keys matching the pattern
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match(pattern);

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                List<String> matchedKeys = scanResult.getResult();

                for (String key : matchedKeys) {
                    // Attempt to retrieve the TerminalSession from Redis
                    TerminalSession terminalSession = null;
                    try {
                        terminalSession = jedisPooled.jsonGet(key, TerminalSession.class);
                    } catch (Exception e) {
                        // if deserialization fails, skip
                        continue;
                    }
                    // If we got a session and it's active, add it to our list
                    if (terminalSession != null && terminalSession.getActive()) {
                        // The terminalId portion is whatever comes after the dot
                        // but we can also read from terminalSession.getTerminalId()
                        activeTerminals.add(terminalSession.getTerminalId());
                    }
                }
            } while (!"0".equals(cursor));
        }

        // If we have multiple terminals, say [A, B], we want "AB"
        // Sorting them alphabetically can be helpful to keep it deterministic:
        Collections.sort(activeTerminals);

        // Join them with no delimiter to produce a string "AB"
        return String.join("", activeTerminals);
    }
}