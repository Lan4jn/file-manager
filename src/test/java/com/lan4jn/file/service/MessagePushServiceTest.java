package com.lan4jn.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessagePushServiceTest {

    @Test
    void pushMessageCreatedShouldSendEventJsonToTargetUser() {
        MessageSocketSessionService sessionService = mock(MessageSocketSessionService.class);
        MessagePushService service = new MessagePushService(sessionService, new ObjectMapper());

        service.pushMessageCreated("bob", Map.of("unreadCount", 3));

        verify(sessionService).sendToUser(eq("bob"), argThat(payload ->
                payload.contains("\"event\":\"message.created\"")
                        && payload.contains("\"unreadCount\":3")));
    }
}
