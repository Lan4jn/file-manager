package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.model.FileInfo;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceTest {

    @Test
    void shouldOnlyExposeLeafDirectoryNameForFolderObject() {
        FileService fileService = new FileService(null, null);

        String fileName = ReflectionTestUtils.invokeMethod(fileService, "getFileName", "docs/reports/2026/");

        assertEquals("2026", fileName);
    }

    @Test
    void shouldReturnFoldersBeforeFilesOnFirstPage() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        FileService fileService = new FileService(minioClient, null);
        ReflectionTestUtils.setField(fileService, "defaultBucket", "bucket-a");
        ReflectionTestUtils.setField(fileService, "publicUrl", "https://files.example.com");

        when(minioClient.listObjects(ArgumentMatchers.any(ListObjectsArgs.class))).thenReturn(Arrays.asList(
                itemResult("docs/", true, 0),
                itemResult("readme.txt", false, 12),
                itemResult("images/", true, 0),
                itemResult("archive.zip", false, 99)
        ));

        PageResult<FileInfo> page = fileService.listFiles("", "bucket-a", "name", "asc", 1, 10);

        assertEquals(List.of("docs", "images", "readme.txt", "archive.zip"),
                page.getItems().stream().map(FileInfo::getFileName).toList());
        assertFalse(page.isHasMore());
        assertEquals(4, page.getTotal());
    }

    @Test
    void shouldPageFoldersFirstThenContinueWithFiles() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        FileService fileService = new FileService(minioClient, null);
        ReflectionTestUtils.setField(fileService, "defaultBucket", "bucket-a");
        ReflectionTestUtils.setField(fileService, "publicUrl", "https://files.example.com");

        List<Result<Item>> listing = Arrays.asList(
                itemResult("z-last-folder/", true, 0),
                itemResult("alpha.txt", false, 1),
                itemResult("beta.txt", false, 2),
                itemResult("a-first-folder/", true, 0),
                itemResult("gamma.txt", false, 3),
                itemResult("delta.txt", false, 4),
                itemResult("epsilon.txt", false, 5),
                itemResult("eta.txt", false, 6),
                itemResult("iota.txt", false, 7),
                itemResult("kappa.txt", false, 8),
                itemResult("lambda.txt", false, 9),
                itemResult("theta.txt", false, 10)
        );

        when(minioClient.listObjects(ArgumentMatchers.any(ListObjectsArgs.class))).thenReturn(listing);

        PageResult<FileInfo> firstPage = fileService.listFiles("", "bucket-a", "name", "asc", 1, 10);
        PageResult<FileInfo> secondPage = fileService.listFiles("", "bucket-a", "name", "asc", 2, 10);

        assertEquals(List.of(
                        "a-first-folder",
                        "z-last-folder",
                        "alpha.txt",
                        "beta.txt",
                        "gamma.txt",
                        "delta.txt",
                        "epsilon.txt",
                        "eta.txt",
                        "iota.txt",
                        "kappa.txt"),
                firstPage.getItems().stream().map(FileInfo::getFileName).toList());
        assertTrue(firstPage.isHasMore());
        assertEquals(List.of("lambda.txt", "theta.txt"),
                secondPage.getItems().stream().map(FileInfo::getFileName).toList());
        assertFalse(secondPage.isHasMore());
        assertEquals(12, secondPage.getTotal());
    }

    private Result<Item> itemResult(String objectName, boolean isDir, long size) throws Exception {
        return new Result<>(new TestItem(objectName, isDir, size));
    }

    private static class TestItem extends Item {
        private final String objectName;
        private final boolean isDir;
        private final long size;

        private TestItem(String objectName, boolean isDir, long size) {
            this.objectName = objectName;
            this.isDir = isDir;
            this.size = size;
        }

        @Override
        public String objectName() {
            return objectName;
        }

        @Override
        public boolean isDir() {
            return isDir;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public ZonedDateTime lastModified() {
            return ZonedDateTime.of(2026, 3, 23, 10, 15, 30, 0, ZoneOffset.ofHours(8));
        }
    }
}

