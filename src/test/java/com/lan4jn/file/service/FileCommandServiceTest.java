package com.lan4jn.file.service;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileCommandServiceTest {

    private MinioClient minioClient;
    private FileDomainSupport support;
    private FileCommandService fileCommandService;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        support = mock(FileDomainSupport.class);
        fileCommandService = new FileCommandService(minioClient, support);
    }

    @Test
    void shouldRejectUploadWhenExtensionIsNotAllowed() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getOriginalFilename()).thenReturn("tool.exe");
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.getFileExtension("tool.exe")).thenReturn("exe");
        when(support.getAllowedExtensions()).thenReturn(Set.of("txt"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileCommandService.uploadFile(multipartFile, "", "", "bucket-a", "http://public"));

        assertTrue(exception.getMessage().contains("exe"));
        verifyNoInteractions(minioClient);
    }

    @Test
    void shouldDeleteNestedObjectsBeforeFolderMarker() throws Exception {
        Result<Item> nestedFileResult = result(item("docs/readme.txt", false));
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(minioClient.listObjects(any())).thenReturn(List.of(nestedFileResult));

        fileCommandService.deleteFile("docs/", "", "bucket-a");

        ArgumentCaptor<io.minio.RemoveObjectArgs> captor = ArgumentCaptor.forClass(io.minio.RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(captor.capture());
        List<io.minio.RemoveObjectArgs> removeArgs = captor.getAllValues();
        assertEquals("docs/readme.txt", removeArgs.get(0).object());
        assertEquals("docs/", removeArgs.get(1).object());
    }

    @SuppressWarnings("unchecked")
    private Result<Item> result(Item item) throws Exception {
        Result<Item> result = mock(Result.class);
        when(result.get()).thenReturn(item);
        return result;
    }

    private Item item(String objectName, boolean dir) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(objectName);
        when(item.isDir()).thenReturn(dir);
        return item;
    }
}
