package edu.help.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import edu.help.websocket.OrderWebSocketHandler;
import edu.help.websocket.BartenderWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final BartenderWebSocketHandler bartenderWebSocketHandler;

    public WebSocketConfig(OrderWebSocketHandler orderWebSocketHandler, BartenderWebSocketHandler bartenderWebSocketHandler) {
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.bartenderWebSocketHandler = bartenderWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWebSocketHandler, "/ws/orders")
                .setAllowedOrigins("*");
        registry.addHandler(bartenderWebSocketHandler, "/ws/bartenders")
                .setAllowedOrigins("*");
    }
}
