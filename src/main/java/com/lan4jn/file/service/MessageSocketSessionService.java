package com.lan4jn.file.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> sessionsByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdToUsername = new ConcurrentHashMap<>();

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
            try {
                session.sendMessage(textMessage);
            } catch (IOException ex) {
                log.warn("Failed to push websocket message to user {}", username, ex);
                unregister(session);
            }
        }
    }
}
