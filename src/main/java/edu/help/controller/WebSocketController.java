package edu.help.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import edu.help.websocket.OrderWebSocketHandler;
import edu.help.websocket.TerminalWebSocketHandler;
import static edu.help.config.ApiConfig.BASE_WS_PATH;


@Configuration
@EnableWebSocket
public class WebSocketController implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    public WebSocketController(OrderWebSocketHandler orderWebSocketHandler, TerminalWebSocketHandler terminalWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.terminalWebSocketHandler = terminalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, BASE_WS_PATH + "/orders")
                .setAllowedOrigins("*");
        registry.addHandler(terminalWebSocketHandler, BASE_WS_PATH + "/terminals")
                .setAllowedOrigins("*");
    }
}