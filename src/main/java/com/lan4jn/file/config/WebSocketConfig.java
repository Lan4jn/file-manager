package com.lan4jn.file.config;

import com.lan4jn.file.service.MessageSocketSessionService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    private static final String ATTR_USERNAME = "username";

    private final MessageSocketSessionService messageSocketSessionService;
    private final MessageWebSocketAuthService messageWebSocketAuthService;

    public WebSocketConfig(MessageSocketSessionService messageSocketSessionService,
                           MessageWebSocketAuthService messageWebSocketAuthService) {
        this.messageSocketSessionService = messageSocketSessionService;
        this.messageWebSocketAuthService = messageWebSocketAuthService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MessageHandler(messageSocketSessionService), "/ws/messages")
                .addInterceptors(new JwtHandshakeInterceptor(messageWebSocketAuthService))
                .setAllowedOriginPatterns("*");
    }

    private static class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final MessageWebSocketAuthService messageWebSocketAuthService;

        private JwtHandshakeInterceptor(MessageWebSocketAuthService messageWebSocketAuthService) {
            this.messageWebSocketAuthService = messageWebSocketAuthService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            List<String> tokens = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .get("token");
            if (tokens == null || tokens.isEmpty()) {
                return false;
            }
            String token = tokens.get(0);
            String username = messageWebSocketAuthService.resolveUsername(token);
            if (username == null || username.trim().isEmpty()) {
                return false;
            }
            attributes.put(ATTR_USERNAME, username);
            if (messageWebSocketAuthService.isManagedCertificateSession(token)) {
                attributes.put("CERT_MANAGED_TOKEN", token);
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }

    private static class MessageHandler extends TextWebSocketHandler {
        private final MessageSocketSessionService messageSocketSessionService;

        private MessageHandler(MessageSocketSessionService messageSocketSessionService) {
            this.messageSocketSessionService = messageSocketSessionService;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            Object username = session.getAttributes().get(ATTR_USERNAME);
            if (username instanceof String) {
                messageSocketSessionService.register((String) username, session);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            messageSocketSessionService.unregister(session);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            messageSocketSessionService.unregister(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            // Server push only; ignore client text frames.
        }
    }
}
