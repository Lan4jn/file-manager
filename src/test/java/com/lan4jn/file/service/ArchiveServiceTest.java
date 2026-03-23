package com.lan4jn.file.service;

import com.lan4jn.file.model.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchiveServiceTest {

    private FileDomainSupport support;
    private FileQueryService fileQueryService;
    private ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        support = mock(FileDomainSupport.class);
        fileQueryService = mock(FileQueryService.class);
        archiveService = new ArchiveService(support, fileQueryService);
    }

    @Test
    void shouldWriteSingleFileIntoBatchZip() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setSize(5L);

        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(fileQueryService.getFileInfo("docs/readme.txt", "bucket-a", "bucket-a", "")).thenReturn(fileInfo);
        when(support.getFileStream("docs/readme.txt", "bucket-a", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        archiveService.downloadBatchAsZip(List.of("docs/readme.txt"), "", "bucket-a", outputStream);

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            java.util.zip.ZipEntry entry = zipInputStream.getNextEntry();
            assertNotNull(entry);
            assertEquals("docs/readme.txt", entry.getName());
        }
    }
}
