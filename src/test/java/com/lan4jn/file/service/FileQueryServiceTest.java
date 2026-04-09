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

        PageResult<FileInfo> pageResult = fileQueryService.listFiles("", "", "bucket-a", "http://public", "name", "asc", true, 1, 10);

        assertEquals(2, pageResult.getItems().size());
        assertTrue(pageResult.getItems().get(0).getIsFolder());
        assertEquals("docs", pageResult.getItems().get(0).getFileName());
        assertEquals("readme.txt", pageResult.getItems().get(1).getFileName());
        assertFalse(pageResult.isHasMore());
    }

    @Test
    void shouldNotForceFoldersFirstWhenExplicitSortingIsRequested() throws Exception {
        Result<Item> folderResult = result(item("docs/", 0L, true));
        Result<Item> fileResult = result(item("a-file.txt", 128L, false));

        FileInfo folderInfo = new FileInfo();
        folderInfo.setObjectName("docs/");
        folderInfo.setFileName("docs");
        folderInfo.setIsFolder(true);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("a-file.txt");
        fileInfo.setFileName("a-file.txt");
        fileInfo.setIsFolder(false);

        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.normalizeFolder("")).thenReturn("");
        when(support.getFileName("a-file.txt")).thenReturn("a-file.txt");
        when(support.buildFileInfo(any(Item.class), any(String.class), any(String.class), any(Boolean.class), any(String.class)))
                .thenReturn(folderInfo, fileInfo);
        when(minioClient.listObjects(any())).thenReturn(List.of(folderResult, fileResult));

        PageResult<FileInfo> pageResult = fileQueryService.listFiles("", "", "bucket-a", "http://public", "name", "asc", false, 1, 10);

        assertEquals(2, pageResult.getItems().size());
        assertEquals("a-file.txt", pageResult.getItems().get(0).getFileName());
        assertFalse(pageResult.getItems().get(0).getIsFolder());
        assertEquals("docs", pageResult.getItems().get(1).getFileName());
        assertTrue(pageResult.getItems().get(1).getIsFolder());
    }

    @Test
    void shouldReturnRealTotalCountForPagedListing() throws Exception {
        Result<Item> folderResult = result(item("docs/", 0L, true));
        Result<Item> fileAResult = result(item("a.txt", 32L, false));
        Result<Item> fileBResult = result(item("b.txt", 64L, false));

        FileInfo folderInfo = new FileInfo();
        folderInfo.setObjectName("docs/");
        folderInfo.setFileName("docs");
        folderInfo.setIsFolder(true);

        FileInfo fileAInfo = new FileInfo();
        fileAInfo.setObjectName("a.txt");
        fileAInfo.setFileName("a.txt");
        fileAInfo.setIsFolder(false);

        FileInfo fileBInfo = new FileInfo();
        fileBInfo.setObjectName("b.txt");
        fileBInfo.setFileName("b.txt");
        fileBInfo.setIsFolder(false);

        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.normalizeFolder("")).thenReturn("");
        when(support.getFileName("a.txt")).thenReturn("a.txt");
        when(support.getFileName("b.txt")).thenReturn("b.txt");
        when(support.buildFileInfo(any(Item.class), any(String.class), any(String.class), any(Boolean.class), any(String.class)))
                .thenReturn(folderInfo, fileAInfo, fileBInfo);
        when(minioClient.listObjects(any())).thenReturn(
                List.of(folderResult, fileAResult, fileBResult),
                List.of(folderResult, fileAResult, fileBResult)
        );

        PageResult<FileInfo> pageResult = fileQueryService.listFiles("", "", "bucket-a", "http://public", "name", "asc", true, 1, 1);

        assertEquals(3, pageResult.getTotal());
        assertEquals(1, pageResult.getItems().size());
        assertTrue(pageResult.isHasMore());
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
