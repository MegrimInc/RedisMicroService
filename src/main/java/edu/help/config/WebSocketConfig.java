package edu.help.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import edu.help.websocket.OrderWebSocketHandler;
import edu.help.websocket.StationWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final StationWebSocketHandler stationWebSocketHandler;

    public WebSocketConfig(OrderWebSocketHandler orderWebSocketHandler, StationWebSocketHandler stationWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.stationWebSocketHandler = stationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/orders")
                .setAllowedOrigins("*");
        registry.addHandler(stationWebSocketHandler, "/ws/stations")
                .setAllowedOrigins("*");
    }
}