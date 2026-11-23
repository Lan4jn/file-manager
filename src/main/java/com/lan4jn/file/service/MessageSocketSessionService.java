package com.lan4jn.file.service;

import com.lan4jn.file.config.MessageWebSocketAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageSocketSessionService {

    private static final Logger log = LoggerFactory.getLogger(MessageSocketSessionService.class);
    private static final String ATTR_CERT_MANAGED_TOKEN = "CERT_MANAGED_TOKEN";

    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdToUsername = new ConcurrentHashMap<>();
    private final MessageWebSocketAuthService authService;

    @Autowired
    public MessageSocketSessionService(MessageWebSocketAuthService authService) {
        this.authService = authService;
    }

    public MessageSocketSessionService() {
        this(null);
    }

    public void register(String username, WebSocketSession session) {
        if (username == null || username.trim().isEmpty() || session == null) {
            return;
        }
        sessionsByUsername.computeIfAbsent(username, key -> new CopyOnWriteArrayList<>()).add(session);
        sessionIdToUsername.put(session.getId(), username);
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        String username = sessionIdToUsername.remove(session.getId());
        if (username == null) {
            return;
        }
        List<WebSocketSession> sessions = sessionsByUsername.get(username);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUsername.remove(username);
        }
    }

    public void sendToUser(String username, String payload) {
        if (username == null || username.trim().isEmpty() || payload == null) {
            return;
        }
        List<WebSocketSession> sessions = sessionsByUsername.get(username);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                unregister(session);
                continue;
            }
            if (session.getAttributes().containsKey(ATTR_CERT_MANAGED_TOKEN)) {
                Object tokenObj = session.getAttributes().get(ATTR_CERT_MANAGED_TOKEN);
                if (!(tokenObj instanceof String token) || token.isBlank()) {
                    closeSession(session);
                    unregister(session);
                    continue;
                }
                if (authService != null) {
                    String currentUsername = authService.resolveUsername(token);
                    if (currentUsername == null || !currentUsername.equals(username)) {
                        closeSession(session);
                        unregister(session);
                        continue;
                    }
                }
            }
            try {
                session.sendMessage(textMessage);
            } catch (IOException ex) {
                log.warn("Failed to push websocket message to user {}", username, ex);
                unregister(session);
            }
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {}
    }
}
