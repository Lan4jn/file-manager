package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareLinkServiceTest {

    @Test
    void shouldTreatLegacyNullTargetTypeAsLink() {
        ShareLink shareLink = new ShareLink();

        assertEquals("LINK", shareLink.getTargetType());
    }

    @Test
    void shouldCreateFolderShareUsingFolderNameAsDisplayName() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        CreateShareRequest request = new CreateShareRequest();
        request.setBucket("bucket-a");
        request.setObjectName("docs/reports/");
        request.setExpirationHours(12);
        request.setPassword("pwd");

        ShareLink shareLink = shareLinkService.createShareLink(request, "tester");

        assertEquals("reports", shareLink.getFileName());
        assertEquals("bucket-a", shareLink.getBucket());
        assertEquals(Boolean.TRUE, shareLink.getIsFolder());
        assertEquals(Boolean.TRUE, shareLink.getHasPassword());
    }

    @Test
    void shouldResolvePasswordProtectedShareAndIncreaseViewCount() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("CODE1234");
        shareLink.setExpireTime(LocalDateTime.now().plusHours(1));
        shareLink.setHasPassword(true);
        shareLink.setPassword("pwd");
        shareLink.setViewCount(1L);

        when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        ShareLink resolved = shareLinkService.resolveShare("CODE1234", "pwd", true);

        assertEquals(2L, resolved.getViewCount());
        assertTrue(resolved.getHasPassword());
    }

    @Test
    void shouldHideShareRecordWithoutCancellingShare() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("CODE1234");
        shareLink.setCreateBy("creator");

        when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        shareLinkService.deleteShareLink("CODE1234", "creator");

        assertEquals(Boolean.TRUE, shareLink.getHiddenByCreator());
        assertEquals(null, shareLink.getCancelled());
        verify(shareLinkRepository).save(shareLink);
    }

    @Test
    void shouldCancelShareLink() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("CODE1234");
        shareLink.setCreateBy("creator");

        when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));
        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        shareLinkService.cancelShareLink("CODE1234", "creator");

        assertEquals(Boolean.TRUE, shareLink.getCancelled());
        verify(shareLinkRepository).save(shareLink);
    }

    @Test
    void shouldRejectCancelledShare() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("CODE1234");
        shareLink.setExpireTime(LocalDateTime.now().plusHours(1));
        shareLink.setCancelled(true);

        when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> shareLinkService.resolveShare("CODE1234", null, false));

        assertEquals("分享已取消", error.getMessage());
    }

    @Test
    void shouldEnrichUserShareRecipients() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("CODE1234");
        shareLink.setCreateBy("creator");
        shareLink.setTargetType("USER");

        UserShareMessage message = new UserShareMessage();
        message.setShareCode("CODE1234");
        message.setRecipientUserId(2L);

        User recipient = new User();
        recipient.setId(2L);
        recipient.setUsername("userA");
        recipient.setNickname("用户A");

        when(shareLinkRepository.findVisibleByCreateByOrderByCreateTimeDesc("creator")).thenReturn(List.of(shareLink));
        when(messageRepository.findByShareCode("CODE1234")).thenReturn(List.of(message));
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, messageRepository, new ShareSupport(), 24);

        List<ShareLink> shareLinks = shareLinkService.getShareLinksByUser("creator");

        assertEquals("用户A(userA)", shareLinks.get(0).getRecipientDisplayText());
    }

    @Test
    void shouldReturnPagedValidSharesOnly() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

        ShareLink activeShare = new ShareLink();
        activeShare.setShareCode("ACTIVE01");
        activeShare.setCreateBy("creator");
        activeShare.setExpireTime(LocalDateTime.now().plusHours(1));
        activeShare.setCancelled(false);

        when(shareLinkRepository.findValidVisibleByCreateBy(
                org.mockito.Mockito.eq("creator"),
                any(LocalDateTime.class),
                org.mockito.Mockito.eq(PageRequest.of(0, 25))
        )).thenReturn(new PageImpl<>(List.of(activeShare), PageRequest.of(0, 25), 1));

        ShareLinkService shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

        PageResult<ShareLink> result = shareLinkService.getShareLinksByUser("creator", 1, 25, true);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(25, result.getSize());
        assertEquals("ACTIVE01", result.getItems().get(0).getShareCode());
    }
}
