package edu.help.websocket;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.help.service.UpdateService;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {
    private final UpdateService updateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BartenderWebSocketHandler(UpdateService updateService) {
        this.updateService = updateService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("Bartender WebSocket message received: " + message.getPayload());

        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);

        String action = (String) payload.get("action");

        switch (action) {
            case "initialize":
                int barID = (int) payload.get("barID");
                String bartenderID = (String) payload.get("bartenderID");

                if (bartenderID == null || bartenderID.isEmpty() || !bartenderID.matches("[a-zA-Z]+")) {
                    session.sendMessage(new TextMessage("Error: Invalid bartenderID. It must be non-empty and contain only alphabetic characters."));
                    return;
                }

                updateService.storeSession(String.valueOf(barID), bartenderID, session);

                session.sendMessage(new TextMessage("Initialization successful for bartender " + bartenderID));
                break;

            default:
                session.sendMessage(new TextMessage("Unknown action: " + action));
                break;
        }
    }
}
