package edu.help.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class BartenderWebSocketHandler extends TextWebSocketHandler {

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle messages received on /ws/bartenders
        System.out.println("Bartender WebSocket message received: " + message.getPayload());

        // Example: Echo the message back
        session.sendMessage(new TextMessage("Echo from /ws/bartenders: " + message.getPayload()));
    }
}
