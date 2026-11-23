package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.service.UserShareMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageControllerTest {

    @Test
    void shouldForwardMessageAndReturnShareCodeFromService() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("NEW12345");
        when(messageService.forwardMessage(9L, "alice", List.of(2L, 3L))).thenReturn(shareLink);

        mockMvc.perform(post("/api/messages/9/forward")
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[2,3]}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shareCode").value("NEW12345"));

        verify(messageService).forwardMessage(9L, "alice", List.of(2L, 3L));
    }

    @Test
    void shouldPassValidOnlyToInboxService() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserShareMessage message = new UserShareMessage();
        message.setId(7L);
        message.setShareCode("ACTIVE01");
        when(messageService.listInbox("alice", 2, 10, true))
                .thenReturn(new PageResult<>(List.of(message), 11, 2, 10));

        mockMvc.perform(get("/api/messages/inbox")
                        .param("page", "2")
                        .param("size", "10")
                        .param("validOnly", "true")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items[0].shareCode").value("ACTIVE01"));
    }

    @Test
    void shouldSubmitFeedback() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/messages/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("{\"category\":\"BUG\",\"title\":\"下载失败\",\"body\":\"系统里有个下载问题\"}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageService).submitFeedback(
                eq("alice"), eq("BUG"), eq("下载失败"), eq("系统里有个下载问题"));
    }

    @Test
    void shouldReturnBadRequestWhenForwardRecipientsMissing() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/messages/9/forward")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(messageService, never()).forwardMessage(eq(9L), eq("alice"), eq(List.of()));
    }

    @Test
    void shouldReturnBadRequestWhenForwardRecipientsEmpty() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/messages/9/forward")
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[]}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldReturnBadRequestWhenFeedbackBodyBlank() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/messages/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("{\"category\":\"BUG\",\"title\":\"下载失败\",\"body\":\"   \"}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldMapForwardBusinessErrorToBadRequest() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(messageService.forwardMessage(9L, "alice", List.of(2L)))
                .thenThrow(new IllegalArgumentException("仅支持转发分享消息"));

        mockMvc.perform(post("/api/messages/9/forward")
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[2]}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持转发分享消息"));
    }

    @Test
    void shouldMapOpenShareBusinessErrorToBadRequest() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(messageService.resolveShareForMessage(9L, "alice"))
                .thenThrow(new IllegalArgumentException("仅支持打开分享消息"));

        mockMvc.perform(get("/api/messages/9/share")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持打开分享消息"));
    }

    @Test
    void shouldReturnBadRequestWhenForwardBodyMalformedJson() throws Exception {
        UserShareMessageService messageService = mock(UserShareMessageService.class);
        MessageController controller = new MessageController(messageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/messages/9/forward")
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[2,}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
