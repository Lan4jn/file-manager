package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.model.FileInfo;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileQueryServiceTest {

    private MinioClient minioClient;
    private FileDomainSupport support;
    private FileQueryService fileQueryService;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        support = mock(FileDomainSupport.class);
        fileQueryService = new FileQueryService(minioClient, support);
    }

    @Test
    void shouldKeepFoldersFirstWhenListingFirstPage() throws Exception {
        Result<Item> folderResult = result(item("docs/", 0L, true));
        Result<Item> fileResult = result(item("readme.txt", 128L, false));

        FileInfo folderInfo = new FileInfo();
        folderInfo.setObjectName("docs/");
        folderInfo.setFileName("docs");
        folderInfo.setIsFolder(true);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setIsFolder(false);

        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.normalizeFolder("")).thenReturn("");
        when(support.getFileName("readme.txt")).thenReturn("readme.txt");
        when(support.buildFileInfo(any(Item.class), any(String.class), any(String.class), any(Boolean.class), any(String.class)))
                .thenReturn(folderInfo, fileInfo);
        when(minioClient.listObjects(any())).thenReturn(
                List.of(folderResult, fileResult),
                List.of(folderResult, fileResult)
        );

        PageResult<FileInfo> pageResult = fileQueryService.listFiles("", "", "bucket-a", "http://public", "name", "asc", 1, 10);

        assertEquals(2, pageResult.getItems().size());
        assertTrue(pageResult.getItems().get(0).getIsFolder());
        assertEquals("docs", pageResult.getItems().get(0).getFileName());
        assertEquals("readme.txt", pageResult.getItems().get(1).getFileName());
        assertFalse(pageResult.isHasMore());
    }

    @SuppressWarnings("unchecked")
    private Result<Item> result(Item item) throws Exception {
        Result<Item> result = mock(Result.class);
        when(result.get()).thenReturn(item);
        return result;
    }

    private Item item(String objectName, long size, boolean dir) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(objectName);
        when(item.size()).thenReturn(size);
        when(item.isDir()).thenReturn(dir);
        return item;
    }
}
