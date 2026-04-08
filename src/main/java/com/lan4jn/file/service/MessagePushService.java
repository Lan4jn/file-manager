package com.lan4jn.file.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MessagePushService {

    private final MessageSocketSessionService messageSocketSessionService;
    private final ObjectMapper objectMapper;

    public MessagePushService(MessageSocketSessionService messageSocketSessionService,
                              ObjectMapper objectMapper) {
        this.messageSocketSessionService = messageSocketSessionService;
        this.objectMapper = objectMapper;
    }

    public void pushMessageCreated(String username, Object payload) {
        push(username, "message.created", payload);
    }

    public void pushMessageDeleted(String username, Object payload) {
        push(username, "message.deleted", payload);
    }

    public void pushMessageRead(String username, Object payload) {
        push(username, "message.read", payload);
    }

    public void pushFeedbackReceived(String username, Object payload) {
        push(username, "feedback.received", payload);
    }

    private void push(String username, String event, Object payload) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("event", event);
        body.put("payload", payload);
        try {
            messageSocketSessionService.sendToUser(username, objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize message push payload", ex);
        }
    }
}
