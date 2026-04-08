package com.lan4jn.file.service;

import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserShareMessageServiceTest {

    @Test
    void shouldResolveRelatedShareCodeWhenPresent() {
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
        when(shareService.peekShareInfo("RELATED-123", null)).thenReturn(expectedLink);

        UserShareMessageService service = new UserShareMessageService(
                userRepository, messageRepository, shareService);

        ShareLink actualLink = service.resolveShareForMessage(1L, "recipient");

        assertSame(expectedLink, actualLink);
        verify(shareService).peekShareInfo("RELATED-123", null);
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
        message.setShareCode("SHARE-003");
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
}
