package edu.help.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import edu.help.websocket.OrderWebSocketHandler;
import edu.help.websocket.TerminalWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    public WebSocketConfig(OrderWebSocketHandler orderWebSocketHandler, TerminalWebSocketHandler terminalWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.terminalWebSocketHandler = terminalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/orders")
                .setAllowedOrigins("*");
        registry.addHandler(terminalWebSocketHandler, "/ws/terminals")
                .setAllowedOrigins("*");
    }
}