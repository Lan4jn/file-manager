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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
        when(support.prepareUpload("tool.exe", 0L, "", "", "bucket-a"))
                .thenThrow(new RuntimeException("不允许上传此类型文件: exe"));

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
        when(support.objectExists("bucket-a", "docs/")).thenReturn(true);

        fileCommandService.deleteFile("docs/", "", "bucket-a");

        ArgumentCaptor<io.minio.RemoveObjectArgs> captor = ArgumentCaptor.forClass(io.minio.RemoveObjectArgs.class);
        verify(minioClient, atLeastOnce()).removeObject(captor.capture());
        List<io.minio.RemoveObjectArgs> removeArgs = captor.getAllValues();
        assertTrue(removeArgs.stream().anyMatch(args -> "docs/readme.txt".equals(args.object())));
        assertTrue(removeArgs.stream().anyMatch(args -> "docs/".equals(args.object())));
    }

    @Test
    void shouldRollbackCopiedTargetWhenMoveDeleteFails() throws Exception {
        when(support.resolveBucket("source-bucket", "bucket-a")).thenReturn("source-bucket");
        when(support.resolveBucket("dest-bucket", "bucket-a")).thenReturn("dest-bucket");
        doThrow(new RuntimeException("delete failed")).when(minioClient)
                .removeObject(argThat(args -> "source.txt".equals(args.object())));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileCommandService.moveFile("source.txt", "dest.txt", "source-bucket", "dest-bucket", "bucket-a"));

        assertTrue(exception.getMessage().contains("移动失败"));
        ArgumentCaptor<io.minio.RemoveObjectArgs> removeCaptor = ArgumentCaptor.forClass(io.minio.RemoveObjectArgs.class);
        verify(minioClient, atLeastOnce()).removeObject(removeCaptor.capture());
        assertTrue(removeCaptor.getAllValues().stream().anyMatch(args -> "dest.txt".equals(args.object())));
    }

    @Test
    void shouldRollbackFolderCreationWhenTestFileCreationFails() throws Exception {
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        doNothing().when(support).createFolderMarker("docs/", "bucket-a");
        doThrow(new RuntimeException("test file failed")).when(support).createTestFile("docs/", "bucket-a");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileCommandService.createFolder("docs", "", "bucket-a", true));

        assertTrue(exception.getMessage().contains("创建文件夹失败"));
        verify(support).deleteObjectQuietly("docs/", "bucket-a");
        verify(support).deleteObjectQuietly("docs/test.txt", "bucket-a");
    }

    @Test
    void shouldRestoreDeletedObjectsWhenBatchDeleteFails() throws Exception {
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        doThrow(new RuntimeException("delete failed")).when(minioClient)
                .removeObject(argThat(args -> "b.txt".equals(args.object())));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileCommandService.deleteFiles(List.of("a.txt", "b.txt"), "", "bucket-a"));

        assertTrue(exception.getMessage().contains("批量删除失败"));
        ArgumentCaptor<io.minio.CopyObjectArgs> copyCaptor = ArgumentCaptor.forClass(io.minio.CopyObjectArgs.class);
        verify(minioClient, atLeastOnce()).copyObject(copyCaptor.capture());
        assertTrue(copyCaptor.getAllValues().stream().anyMatch(args -> "a.txt".equals(args.object())));
    }

    @Test
    void shouldRenameFileWithinSameFolder() throws Exception {
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.objectExists("bucket-a", "docs/renamed.txt")).thenReturn(false);

        String target = fileCommandService.rename("docs/source.txt", "renamed.txt", "", "bucket-a", "OVERWRITE");

        assertEquals("docs/renamed.txt", target);
        verify(minioClient).copyObject(argThat(args -> "docs/renamed.txt".equals(args.object())));
    }

    @Test
    void shouldAutoRenameWhenRenameConflictPolicyRequested() throws Exception {
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(support.objectExists("bucket-a", "docs/renamed.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/renamed_2.txt")).thenReturn(false);

        String target = fileCommandService.rename("docs/source.txt", "renamed.txt", "", "bucket-a", "RENAME");

        assertEquals("docs/renamed_2.txt", target);
        verify(minioClient).copyObject(argThat(args -> "docs/renamed_2.txt".equals(args.object())));
    }

    @Test
    void shouldRejectRenameWhenTargetNameContainsPathSeparator() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileCommandService.rename("docs/source.txt", "bad/name.txt", "", "bucket-a", "OVERWRITE"));

        assertTrue(exception.getMessage().contains("名称不合法"));
    }

    @Test
    void shouldRenameFolderWithinSameParent() throws Exception {
        Result<Item> nestedFileResult = result(item("docs/source/readme.txt", false));
        when(support.resolveBucket("", "bucket-a")).thenReturn("bucket-a");
        when(minioClient.listObjects(any())).thenReturn(List.of(nestedFileResult));
        when(support.objectExists("bucket-a", "docs/source/")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/renamed/")).thenReturn(false);

        String target = fileCommandService.rename("docs/source/", "renamed", "", "bucket-a", "OVERWRITE");

        assertEquals("docs/renamed/", target);
        verify(minioClient).copyObject(argThat(args -> "docs/renamed/readme.txt".equals(args.object())));
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
