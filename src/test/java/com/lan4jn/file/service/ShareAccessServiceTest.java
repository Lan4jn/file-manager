package com.lan4jn.file.service;

import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareAccessServiceTest {

    @Test
    void shouldReturnFolderPlaceholderForCollectionShare() {
        FileService fileService = mock(FileService.class);
        ConfigService configService = mock(ConfigService.class);
        ShareLinkService shareLinkService = mock(ShareLinkService.class);
        ShareSupport shareSupport = new ShareSupport();

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setObjectName("docs/");
        shareLink.setFileName("docs");
        shareLink.setBucket("bucket-a");
        shareLink.setIsFolder(true);
        shareLink.setExpireTime(LocalDateTime.now().plusHours(1));

        when(shareLinkService.resolveShare("SHARE001", null, false)).thenReturn(shareLink);

        ShareAccessService shareAccessService = new ShareAccessService(
                fileService, configService, shareLinkService, shareSupport);

        FileInfo fileInfo = shareAccessService.getFileByShareCode("SHARE001", null);

        assertEquals("docs/", fileInfo.getObjectName());
        assertEquals("docs", fileInfo.getFileName());
        assertEquals(Boolean.TRUE, fileInfo.getIsFolder());
    }

    @Test
    void shouldListRootItemsForBatchShareWithFoldersBeforeFiles() {
        FileService fileService = mock(FileService.class);
        ConfigService configService = mock(ConfigService.class);
        ShareLinkService shareLinkService = mock(ShareLinkService.class);
        ShareSupport shareSupport = new ShareSupport();

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE002");
        shareLink.setObjectName(ShareSupport.BATCH_PREFIX + "[\"z-folder/\",\"alpha.txt\"]");
        shareLink.setBucket("bucket-a");
        shareLink.setIsFolder(true);
        shareLink.setExpireTime(LocalDateTime.now().plusHours(1));

        FileInfo alpha = new FileInfo();
        alpha.setObjectName("alpha.txt");
        alpha.setFileName("alpha.txt");
        alpha.setIsFolder(false);
        alpha.setSize(1L);

        when(shareLinkService.resolveShare("SHARE002", null, false)).thenReturn(shareLink);
        when(fileService.getFileInfo("alpha.txt", "bucket-a")).thenReturn(alpha);

        ShareAccessService shareAccessService = new ShareAccessService(
                fileService, configService, shareLinkService, shareSupport);

        List<FileInfo> items = shareAccessService.listSharedItems("SHARE002", null, "");

        assertEquals(List.of("z-folder", "alpha.txt"), items.stream().map(FileInfo::getFileName).toList());
    }
}
