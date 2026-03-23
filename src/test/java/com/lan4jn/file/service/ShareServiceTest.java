package com.lan4jn.file.service;

import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareServiceTest {

    @Test
    void shouldCreateBatchShareWithStructuredBatchPayload() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        ConfigService configService = mock(ConfigService.class);

        when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareService shareService = new ShareService(
                fileService, shareLinkRepository, shareFileRecordRepository, configService, 24);

        ShareLink shareLink = shareService.createBatchShareLink(
                List.of("docs/", "readme.txt"), "bucket-a", "tester", null, "1234");

        assertTrue(shareLink.getObjectName().startsWith(ShareSupport.BATCH_PREFIX));
        assertEquals("bucket-a", shareLink.getBucket());
        assertEquals("tester", shareLink.getCreateBy());
        assertEquals(Boolean.TRUE, shareLink.getIsFolder());
        assertEquals(Boolean.TRUE, shareLink.getHasPassword());
        assertEquals("1234", shareLink.getPassword());
    }

    @Test
    void shouldListBatchShareRootItemsWithFoldersBeforeFiles() {
        FileService fileService = mock(FileService.class);
        ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
        ConfigService configService = mock(ConfigService.class);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("ABCD1234");
        shareLink.setBucket("bucket-a");
        shareLink.setObjectName(ShareSupport.BATCH_PREFIX + "[\"z-folder/\",\"alpha.txt\"]");
        shareLink.setFileName("batch");
        shareLink.setIsFolder(true);
        shareLink.setHasPassword(false);
        shareLink.setExpireTime(LocalDateTime.now().plusHours(1));

        FileInfo alpha = new FileInfo();
        alpha.setObjectName("alpha.txt");
        alpha.setFileName("alpha.txt");
        alpha.setIsFolder(false);
        alpha.setSize(12L);

        when(shareLinkRepository.findByShareCode("ABCD1234")).thenReturn(Optional.of(shareLink));
        when(fileService.getFileInfo("alpha.txt", "bucket-a")).thenReturn(alpha);

        ShareService shareService = new ShareService(
                fileService, shareLinkRepository, shareFileRecordRepository, configService, 24);

        List<FileInfo> items = shareService.listSharedItems("ABCD1234", null, "");

        assertEquals(List.of("z-folder", "alpha.txt"),
                items.stream().map(FileInfo::getFileName).toList());
        assertEquals(List.of(true, false),
                items.stream().map(file -> Boolean.TRUE.equals(file.getIsFolder())).toList());
    }
}
