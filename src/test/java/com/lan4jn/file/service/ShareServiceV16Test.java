package com.lan4jn.file.service;

import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareServiceV16Test {

    @Test
    void shouldGenerateFourDigitPasswordForLinkShareWhenPasswordMissing() throws Exception {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository userShareMessageRepository = mock(UserShareMessageRepository.class);
        ConfigService configService = mock(ConfigService.class);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName("readme.txt");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareService shareService = new ShareService(
                fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, userShareMessageRepository, configService, 24);

        CreateShareRequest request = new CreateShareRequest();
        request.setObjectName("docs/readme.txt");
        request.setBucket("bucket-a");
        request.setPassword(null);
        request.setTargetType("LINK");

        ShareLink shareLink = shareService.createShareLink(request, "admin");

        assertNotNull(shareLink.getTargetType());
        assertEquals("LINK", shareLink.getTargetType());
        assertNotNull(shareLink.getPassword());
        assertTrue(shareLink.getPassword().matches("\\d{4}"));
    }

    @Test
    void shouldCreateUserShareAndDispatchMessagesWithoutPassword() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository userShareMessageRepository = mock(UserShareMessageRepository.class);
        ConfigService configService = mock(ConfigService.class);

        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "alice")));
        when(userRepository.findById(3L)).thenReturn(Optional.of(buildUser(3L, "bob")));

        ShareService shareService = new ShareService(
                fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, userShareMessageRepository, configService, 24);

        CreateShareRequest request = new CreateShareRequest();
        request.setObjectName("docs/");
        request.setBucket("bucket-a");
        request.setTargetType("USER");
        request.setRecipientUserIds(List.of(2L, 3L));
        request.setExpirationHours(24);

        ShareLink shareLink = shareService.createShareLink(request, "admin");

        assertEquals("USER", shareLink.getTargetType());
        assertEquals(Boolean.FALSE, shareLink.getHasPassword());
        assertEquals(null, shareLink.getPassword());
        verify(userShareMessageRepository).saveAll(argThat(messages -> {
            List<com.lan4jn.file.model.UserShareMessage> materialized = new ArrayList<>();
            for (Object message : messages) {
                materialized.add((com.lan4jn.file.model.UserShareMessage) message);
            }
            return materialized.size() == 2
                    && materialized.stream().allMatch(message ->
                    shareLink.getShareCode().equals(message.getShareCode())
                            && "admin".equals(message.getSenderUsername()));
        }));
    }

    @Test
    void shouldCreateBatchUserShareAndDispatchMessagesWithoutPassword() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository userShareMessageRepository = mock(UserShareMessageRepository.class);
        ConfigService configService = mock(ConfigService.class);

        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buildUser(2L, "alice")));

        ShareService shareService = new ShareService(
                fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, userShareMessageRepository, configService, 24);

        ShareLink shareLink = shareService.createBatchShareLink(
                List.of("docs/", "images/logo.png"), "bucket-a", "admin", 24, null, "USER", List.of(2L));

        assertEquals("USER", shareLink.getTargetType());
        assertEquals(Boolean.FALSE, shareLink.getHasPassword());
        assertEquals(null, shareLink.getPassword());
        verify(userShareMessageRepository).saveAll(argThat(messages -> {
            List<com.lan4jn.file.model.UserShareMessage> materialized = new ArrayList<>();
            for (Object message : messages) {
                materialized.add((com.lan4jn.file.model.UserShareMessage) message);
            }
            return materialized.size() == 1
                    && shareLink.getShareCode().equals(materialized.get(0).getShareCode())
                    && "admin".equals(materialized.get(0).getSenderUsername());
        }));
    }

    @Test
    void shouldWrapShareCreationInTransaction() throws Exception {
        Method method = ShareService.class.getMethod("createShareLink", CreateShareRequest.class, String.class);

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
