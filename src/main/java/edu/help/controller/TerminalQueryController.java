package edu.help.controller;

import edu.help.dto.BartenderSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

// Could also be @RestController, which automatically adds @ResponseBody
@RestController
@RequestMapping("/http")
public class TerminalQueryController {

    private final JedisPooled jedisPooled;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    @Autowired
    public TerminalQueryController(JedisPooled jedisPooled, JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPooled = jedisPooled;
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    /**
     * Example GET endpoint:  https://www.barzzy.site/http/checkTerminals?barID=123
     *
     * Returns a string containing the list of active terminals, e.g. "AB", or "" if none.
     */
    @GetMapping("/checkTerminals")
    public ResponseEntity<String> checkTerminalsGET(@RequestParam("barID") int barId) {
        try {
            // We'll reuse a method below that does the actual check
            String activeTerminals = getActiveTerminals(barId);
            return ResponseEntity.ok(activeTerminals);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error checking terminals: " + e.getMessage());
        }
    }

    /**
     * Example POST endpoint if you prefer POST:
     *   https://www.barzzy.site/http/checkTerminals
     * with body form-data or JSON containing barID
     */
    @PostMapping("/checkTerminals")
    public ResponseEntity<String> checkTerminalsPOST(@RequestParam("barID") int barId) {
        try {
            // Same logic as GET
            String activeTerminals = getActiveTerminals(barId);
            return ResponseEntity.ok(activeTerminals);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Error checking terminals: " + e.getMessage());
        }
    }

    /**
     * The main logic that scans Redis for <barId>.<bartenderID> keys,
     * deserializes them into BartenderSession objects, and
     * collects the ones that are 'active'.
     */
    private String getActiveTerminals(int barId) throws Exception {
        // Example pattern: "123.[a-zA-Z]*" for barId=123
        String pattern = barId + ".[a-zA-Z]*";
        List<String> activeBartenders = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            // We'll scan Redis for keys matching the pattern
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match(pattern);

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                List<String> matchedKeys = scanResult.getResult();

                for (String key : matchedKeys) {
                    // Attempt to retrieve the BartenderSession from Redis
                    BartenderSession bartenderSession = null;
                    try {
                        bartenderSession = jedisPooled.jsonGet(key, BartenderSession.class);
                    } catch (Exception e) {
                        // if deserialization fails, skip
                        continue;
                    }
                    // If we got a session and it's active, add it to our list
                    if (bartenderSession != null && bartenderSession.getActive()) {
                        // The bartenderID portion is whatever comes after the dot
                        // but we can also read from bartenderSession.getBartenderId()
                        activeBartenders.add(bartenderSession.getBartenderId());
                    }
                }
            } while (!"0".equals(cursor));
        }

        // If we have multiple bartenders, say [A, B], we want "AB"
        // Sorting them alphabetically can be helpful to keep it deterministic:
        Collections.sort(activeBartenders);

        // Join them with no delimiter to produce a string "AB"
        return String.join("", activeBartenders);
    }
}
