package com.lan4jn.file.service;

import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserShareMessageServiceTest {

    @Test
    void shouldPreferShareCodeOverRelatedShareCodeWhenBothPresent() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(1L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-001");
        message.setRelatedShareCode("RELATED-123");

        ShareLink expectedLink = new ShareLink();
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(1L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("SHARE-001", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(1L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("SHARE-001", null);
    }

    @Test
    void shouldFallBackToShareCodeWhenRelatedShareCodeMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(2L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-002");
        message.setRelatedShareCode(null);

        ShareLink expectedLink = new ShareLink();
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(2L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("SHARE-002", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(2L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("SHARE-002", null);
    }

    @Test
    void shouldTrimRelatedShareCodeBeforePeek() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(3L);
        message.setRecipientUserId(42L);
        message.setShareCode("   ");
        message.setRelatedShareCode("  RELATED-TRIM  ");

        ShareLink expectedLink = new ShareLink();
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(3L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("RELATED-TRIM", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(3L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("RELATED-TRIM", null);
    }

    @Test
    void shouldFallbackToShareCodeWhenRelatedShareCodeOnlyWhitespace() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(5L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-005");
        message.setRelatedShareCode("    ");

        ShareLink expectedLink = new ShareLink();
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(5L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("SHARE-005", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(5L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("SHARE-005", null);
    }

    @Test
    void shouldNotMarkReadIfPeekFails() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(6L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-006");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(6L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("SHARE-006", null)).thenThrow(new RuntimeException("invalid share"));

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        assertThrows(RuntimeException.class, () -> service.resolveShareForMessage(6L, "recipient"));
        verify(messageRepository, never()).save(message);
        verify(shareService).peekShareInfo("SHARE-006", null);
        assertSame(null, message.getRead());
    }

    @Test
    void resolveShareForMessageQueriesOwnershipOnlyOnce() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(4L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-004");

        ShareLink expectedLink = new ShareLink();
        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(4L, 42L)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(UserShareMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shareService.peekShareInfo("SHARE-004", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(4L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("SHARE-004", null);
        verify(messageRepository, times(1)).findByIdAndRecipientUserIdAndDeletedFalse(4L, 42L);
    }

    @Test
    void markReadDoesNotSaveWhenAlreadyRead() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(7L);
        message.setRecipientUserId(42L);
        message.setRead(true);

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(7L, 42L)).thenReturn(Optional.of(message));

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        service.markRead(7L, "recipient");

        verify(messageRepository, never()).save(message);
    }

    @Test
    void resolveShareForMessageThrowsWhenMessageMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(8L, 42L)).thenReturn(Optional.empty());

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        assertThrows(RuntimeException.class, () -> service.resolveShareForMessage(8L, "recipient"));
        verify(shareService, never()).peekShareInfo(any(), any());
    }

    @Test
    void resolveShareForMessageShouldRejectNonShareMessageType() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(16L);
        message.setRecipientUserId(42L);
        message.setMessageType("FEEDBACK");
        message.setShareCode("SHARE-016");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(16L, 42L)).thenReturn(Optional.of(message));

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveShareForMessage(16L, "recipient"));
        assertEquals("仅支持打开分享消息", exception.getMessage());
        verify(shareService, never()).peekShareInfo(any(), any());
    }

    @Test
    void resolveShareForMessageShouldRejectWhenLinkedShareCodeMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User user = new User();
        user.setId(42L);
        user.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(17L);
        message.setRecipientUserId(42L);
        message.setMessageType("SHARE");
        message.setShareCode("  ");
        message.setRelatedShareCode("  ");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(user));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(17L, 42L)).thenReturn(Optional.of(message));

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveShareForMessage(17L, "recipient"));
        assertEquals("消息未关联可打开分享", exception.getMessage());
        verify(shareService, never()).peekShareInfo(any(), any());
    }

    @Test
    void shouldForwardMessageByCreatingNewUserShareWithDefaultExpirationSemantics() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(10L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-010");
        message.setOriginSenderUsername("origin-user");
        message.setSenderUsername("forwarder-a");

        ShareLink sourceShare = new ShareLink();
        sourceShare.setShareCode("SHARE-010");
        sourceShare.setBucket("bucket-a");
        sourceShare.setObjectName("docs/readme.md");

        ShareLink forwardedShare = new ShareLink();
        forwardedShare.setShareCode("NEW-1010");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(10L, 42L)).thenReturn(Optional.of(message));
        when(shareService.peekShareInfo("SHARE-010", null)).thenReturn(sourceShare);
        when(shareService.createShareLink(any(CreateShareRequest.class), eq("recipient"),
                any(ShareService.UserShareMessageDispatchContext.class))).thenReturn(forwardedShare);

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);
        ShareLink actual = service.forwardMessage(10L, "recipient", List.of(2L, 3L));

        assertSame(forwardedShare, actual);

        ArgumentCaptor<CreateShareRequest> requestCaptor = ArgumentCaptor.forClass(CreateShareRequest.class);
        verify(shareService).createShareLink(requestCaptor.capture(), eq("recipient"),
                any(ShareService.UserShareMessageDispatchContext.class));
        CreateShareRequest request = requestCaptor.getValue();
        assertEquals("USER", request.getTargetType());
        assertEquals("bucket-a", request.getBucket());
        assertEquals("docs/readme.md", request.getObjectName());
        assertEquals(List.of(2L, 3L), request.getRecipientUserIds());
        assertEquals(null, request.getExpirationHours());
    }

    @Test
    void shouldForwardMessageKeepChainButNotPinOldRelatedShareCode() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(11L);
        message.setRecipientUserId(42L);
        message.setShareCode("SHARE-011");
        message.setRelatedShareCode("ROOT-001");
        message.setOriginSenderUsername("origin-user");
        message.setSenderUsername("forwarder-a");
        message.setForwardedByUsername("forwarder-a");

        ShareLink sourceShare = new ShareLink();
        sourceShare.setShareCode("SHARE-011");
        sourceShare.setBucket("bucket-a");
        sourceShare.setObjectName("docs/report.pdf");

        ShareLink forwardedShare = new ShareLink();
        forwardedShare.setShareCode("NEW-1111");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(11L, 42L)).thenReturn(Optional.of(message));
        when(shareService.peekShareInfo("SHARE-011", null)).thenReturn(sourceShare);
        when(shareService.createShareLink(any(CreateShareRequest.class), eq("recipient"),
                any(ShareService.UserShareMessageDispatchContext.class))).thenReturn(forwardedShare);

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);
        service.forwardMessage(11L, "recipient", List.of(5L));

        verify(shareService).createShareLink(
                any(CreateShareRequest.class),
                eq("recipient"),
                argThat(context -> "origin-user".equals(context.getOriginSenderUsername())
                        && "recipient".equals(context.getForwardedByUsername())
                        && context.getRelatedShareCode() == null
                        && "SHARE-011".equals(context.getConversationKey()))
        );
    }

    @Test
    void shouldForwardBatchMessageByCreatingBatchUserShare() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(12L);
        message.setRecipientUserId(42L);
        message.setShareCode("BATCH-OLD");
        message.setSenderUsername("origin-user");

        ShareLink sourceShare = new ShareLink();
        sourceShare.setShareCode("BATCH-OLD");
        sourceShare.setBucket("bucket-a");
        sourceShare.setObjectName("batchjson:[\"docs/\",\"images/logo.png\"]");

        ShareLink forwardedShare = new ShareLink();
        forwardedShare.setShareCode("BATCH-NEW");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(12L, 42L)).thenReturn(Optional.of(message));
        when(shareService.peekShareInfo("BATCH-OLD", null)).thenReturn(sourceShare);
        when(shareService.createBatchShareLink(
                any(List.class), eq("bucket-a"), eq("recipient"), eq(null), eq(null), eq("USER"), eq(List.of(8L)),
                any(ShareService.UserShareMessageDispatchContext.class))).thenReturn(forwardedShare);

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);
        ShareLink actual = service.forwardMessage(12L, "recipient", List.of(8L));

        assertSame(forwardedShare, actual);
        verify(shareService).createBatchShareLink(
                eq(List.of("docs/", "images/logo.png")),
                eq("bucket-a"),
                eq("recipient"),
                eq(null),
                eq(null),
                eq("USER"),
                eq(List.of(8L)),
                any(ShareService.UserShareMessageDispatchContext.class)
        );
    }

    @Test
    void shouldRejectForwardWhenMessageTypeIsNotShare() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(13L);
        message.setRecipientUserId(42L);
        message.setMessageType("FEEDBACK");
        message.setShareCode("SHARE-013");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(13L, 42L)).thenReturn(Optional.of(message));

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.forwardMessage(13L, "recipient", List.of(2L))
        );
        assertEquals("仅支持转发分享消息", exception.getMessage());
        verify(shareService, never()).peekShareInfo(any(), any());
    }

    @Test
    void shouldRejectForwardWhenLinkedShareCodeMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(14L);
        message.setRecipientUserId(42L);
        message.setMessageType("SHARE");
        message.setShareCode("   ");
        message.setRelatedShareCode("  ");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(14L, 42L)).thenReturn(Optional.of(message));

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.forwardMessage(14L, "recipient", List.of(2L))
        );
        assertEquals("消息未关联可转发分享", exception.getMessage());
        verify(shareService, never()).peekShareInfo(any(), any());
    }

    @Test
    void shouldDeduplicateRecipientsAndExcludeSelfWhenForwarding() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User currentUser = new User();
        currentUser.setId(42L);
        currentUser.setUsername("recipient");

        UserShareMessage message = new UserShareMessage();
        message.setId(15L);
        message.setRecipientUserId(42L);
        message.setMessageType("SHARE");
        message.setShareCode("SHARE-015");

        ShareLink sourceShare = new ShareLink();
        sourceShare.setShareCode("SHARE-015");
        sourceShare.setBucket("bucket-a");
        sourceShare.setObjectName("docs/readme.md");

        ShareLink forwardedShare = new ShareLink();
        forwardedShare.setShareCode("NEW-1515");

        when(userRepository.findByUsername("recipient")).thenReturn(Optional.of(currentUser));
        when(messageRepository.findByIdAndRecipientUserIdAndDeletedFalse(15L, 42L)).thenReturn(Optional.of(message));
        when(shareService.peekShareInfo("SHARE-015", null)).thenReturn(sourceShare);
        when(shareService.createShareLink(any(CreateShareRequest.class), eq("recipient"),
                any(ShareService.UserShareMessageDispatchContext.class))).thenReturn(forwardedShare);

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);
        service.forwardMessage(15L, "recipient", List.of(42L, 2L, 2L, 3L));

        verify(shareService).createShareLink(
                argThat(request -> List.of(2L, 3L).equals(request.getRecipientUserIds())),
                eq("recipient"),
                any(ShareService.UserShareMessageDispatchContext.class)
        );
    }

    @Test
    void submitFeedbackShouldOnlySendToEnabledAdmins() {
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);
        ShareService shareService = mock(ShareService.class);

        User sender = new User();
        sender.setId(1L);
        sender.setUsername("alice");
        sender.setEnabled(true);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        Role userRole = new Role();
        userRole.setCode("USER");

        User enabledAdmin = new User();
        enabledAdmin.setId(2L);
        enabledAdmin.setUsername("admin-a");
        enabledAdmin.setEnabled(true);
        enabledAdmin.setRoles(Set.of(adminRole));

        User disabledAdmin = new User();
        disabledAdmin.setId(3L);
        disabledAdmin.setUsername("admin-b");
        disabledAdmin.setEnabled(false);
        disabledAdmin.setRoles(Set.of(adminRole));

        User enabledNormalUser = new User();
        enabledNormalUser.setId(4L);
        enabledNormalUser.setUsername("user-a");
        enabledNormalUser.setEnabled(true);
        enabledNormalUser.setRoles(Set.of(userRole));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findAll()).thenReturn(List.of(enabledAdmin, disabledAdmin, enabledNormalUser));
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserShareMessageService service = new UserShareMessageService(userRepository, messageRepository, shareService);
        service.submitFeedback("alice", "SUGGESTION", "优化分享体验", "请支持按目录筛选");

        verify(messageRepository).saveAll(argThat(messages -> {
            List<UserShareMessage> list = new ArrayList<>();
            messages.forEach(list::add);
            if (list.size() != 1) {
                return false;
            }
            UserShareMessage only = list.get(0);
            return Long.valueOf(2L).equals(only.getRecipientUserId())
                    && "alice".equals(only.getSenderUsername())
                    && "FEEDBACK".equals(only.getMessageType())
                    && "[SUGGESTION] 优化分享体验".equals(only.getTitle())
                    && "请支持按目录筛选".equals(only.getBody())
                    && only.getCreateTime() != null
                    && Boolean.FALSE.equals(only.getDeleted())
                    && Boolean.FALSE.equals(only.getRead());
        }));
    }
}
