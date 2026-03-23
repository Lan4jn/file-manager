package com.lan4jn.file.service;

import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareLinkServiceTest {

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
}
