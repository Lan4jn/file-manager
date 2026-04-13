package com.lan4jn.file.service;

import com.lan4jn.file.dto.FileTransferExecuteRequest;
import com.lan4jn.file.dto.FileTransferExecuteResponse;
import com.lan4jn.file.dto.FileTransferPreviewRequest;
import com.lan4jn.file.dto.FileTransferPreviewResponse;
import com.lan4jn.file.model.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileTransferServiceTest {

    private FileDomainSupport support;
    private FileCommandService commandService;
    private FileQueryService queryService;
    private FileTransferService service;

    @BeforeEach
    void setUp() {
        support = mock(FileDomainSupport.class);
        commandService = mock(FileCommandService.class);
        queryService = mock(FileQueryService.class);
        service = new FileTransferService(support, commandService, queryService);
        when(support.resolveBucket("bucket-a", "file-manager")).thenReturn("bucket-a");
        when(support.normalizeFolder(anyString())).thenAnswer(invocation -> {
            String folder = invocation.getArgument(0, String.class);
            if (folder == null || folder.isEmpty() || folder.endsWith("/")) {
                return folder;
            }
            return folder + "/";
        });
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        when(queryService.listFilesRecursive("archive/docs/", "bucket-a", "file-manager")).thenReturn(List.of());
        when(queryService.listFilesRecursive("archive/readme.txt", "bucket-a", "file-manager")).thenReturn(List.of());
    }

    @Test
    void shouldMarkProductionConstructorWithAutowired() throws NoSuchMethodException {
        Constructor<FileTransferService> constructor =
                FileTransferService.class.getConstructor(io.minio.MinioClient.class, ConfigService.class);

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void shouldRejectMoveIntoSameFolder() {
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        FileTransferPreviewRequest request = previewRequest("MOVE", List.of("docs/readme.txt"), "docs/");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isValid());
        assertEquals("不能移动到原目录", response.getMessage());
        verify(commandService, never()).copyFile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectFolderMoveIntoDescendantFolder() {
        when(support.objectExists("bucket-a", "docs/")).thenReturn(true);
        FileTransferPreviewRequest request = previewRequest("MOVE", List.of("docs/"), "docs/sub/");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isValid());
        assertEquals("不能移动或复制到自身子目录", response.getMessage());
    }

    @Test
    void shouldReportConflictDuringPreview() {
        when(support.objectExists("bucket-a", "archive/readme.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        FileTransferPreviewRequest request = previewRequest("COPY", List.of("docs/readme.txt"), "archive/");

        FileTransferPreviewResponse response = service.preview(request);

        assertTrue(response.isValid());
        assertTrue(response.isHasConflicts());
        assertEquals("archive/readme.txt", response.getItems().get(0).getTarget());
        assertTrue(response.getItems().get(0).isConflict());
    }

    @Test
    void shouldRenameTargetWhenConflictPolicyIsRename() {
        when(support.objectExists("bucket-a", "archive/readme.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "archive/readme(1).txt")).thenReturn(false);
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        FileTransferPreviewRequest request = previewRequest("COPY", List.of("docs/readme.txt"), "archive/");
        request.setConflictPolicy("RENAME");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isHasConflicts());
        assertEquals("archive/readme(1).txt", response.getItems().get(0).getTarget());
    }

    @Test
    void shouldExecuteCopyForSingleFile() {
        when(support.objectExists("bucket-a", "archive/readme.txt")).thenReturn(false);
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        FileTransferExecuteRequest request = executeRequest("COPY", List.of("docs/readme.txt"), "archive/", "OVERWRITE");

        FileTransferExecuteResponse response = service.execute(request);

        assertEquals(1, response.getSuccessCount());
        assertEquals(List.of("archive/readme.txt"), response.getResolvedTargets());
        verify(commandService).copyFile("docs/readme.txt", "archive/readme.txt", "bucket-a", "bucket-a", "file-manager");
    }

    @Test
    void shouldExecuteMoveForFolder() {
        FileInfo child = new FileInfo();
        child.setObjectName("docs/a.txt");
        when(queryService.listFilesRecursive("docs/", "bucket-a", "file-manager")).thenReturn(List.of(child));
        when(queryService.listFilesRecursive("archive/docs/", "bucket-a", "file-manager")).thenReturn(List.of());
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        doNothing().when(commandService).createFolder("archive/docs/", "bucket-a", "file-manager", false);

        FileTransferExecuteRequest request = executeRequest("MOVE", List.of("docs/"), "archive/", "OVERWRITE");

        FileTransferExecuteResponse response = service.execute(request);

        assertEquals(1, response.getSuccessCount());
        verify(commandService).createFolder("archive/docs/", "bucket-a", "file-manager", false);
        verify(commandService).copyFile("docs/a.txt", "archive/docs/a.txt", "bucket-a", "bucket-a", "file-manager");
        verify(commandService).deleteFile("docs/", "bucket-a", "file-manager");
    }

    @Test
    void shouldRejectMissingSourceFileDuringPreview() {
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        when(support.objectExists("bucket-a", "missing.txt")).thenReturn(false);
        FileTransferPreviewRequest request = previewRequest("COPY", List.of("missing.txt"), "archive/");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isValid());
        assertEquals("源文件不存在: missing.txt", response.getMessage());
        assertEquals("源文件不存在: missing.txt", response.getItems().get(0).getMessage());
    }

    @Test
    void shouldRejectMissingFolderSourceDuringPreview() {
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        when(queryService.listFilesRecursive("missing/", "bucket-a", "file-manager")).thenReturn(List.of());
        FileTransferPreviewRequest request = previewRequest("COPY", List.of("missing/"), "archive/");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isValid());
        assertEquals("源目录不存在: missing/", response.getMessage());
        assertEquals("源目录不存在: missing/", response.getItems().get(0).getMessage());
    }

    @Test
    void shouldRejectMissingTargetFolderDuringPreview() {
        when(support.objectExists("bucket-a", "docs/readme.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "missing-target/")).thenReturn(false);
        when(queryService.listFilesRecursive("missing-target/", "bucket-a", "file-manager")).thenReturn(List.of());
        FileTransferPreviewRequest request = previewRequest("COPY", List.of("docs/readme.txt"), "missing-target/");

        FileTransferPreviewResponse response = service.preview(request);

        assertFalse(response.isValid());
        assertEquals("目标目录不存在: missing-target/", response.getMessage());
        assertEquals("目标目录不存在: missing-target/", response.getItems().get(0).getMessage());
    }

    @Test
    void shouldRollbackCompletedItemsWhenBatchExecuteFails() {
        when(support.objectExists("bucket-a", "archive/")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/a.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "docs/b.txt")).thenReturn(true);
        when(support.objectExists("bucket-a", "archive/a.txt")).thenReturn(false);
        when(support.objectExists("bucket-a", "archive/b.txt")).thenReturn(false);
        doThrow(new RuntimeException("copy failed")).when(commandService)
                .copyFile("docs/b.txt", "archive/b.txt", "bucket-a", "bucket-a", "file-manager");
        FileTransferExecuteRequest request = executeRequest("COPY", List.of("docs/a.txt", "docs/b.txt"), "archive/", "OVERWRITE");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.execute(request));

        assertEquals("文件转移失败: copy failed", exception.getMessage());
        verify(commandService).copyFile("docs/a.txt", "archive/a.txt", "bucket-a", "bucket-a", "file-manager");
        verify(commandService).deleteFile("archive/a.txt", "bucket-a", "file-manager");
    }

    private FileTransferPreviewRequest previewRequest(String operation, List<String> sources, String targetFolder) {
        FileTransferPreviewRequest request = new FileTransferPreviewRequest();
        request.setOperation(operation);
        request.setBucket("bucket-a");
        request.setSources(sources);
        request.setTargetFolder(targetFolder);
        request.setConflictPolicy("ASK");
        return request;
    }

    private FileTransferExecuteRequest executeRequest(String operation, List<String> sources, String targetFolder, String conflictPolicy) {
        FileTransferExecuteRequest request = new FileTransferExecuteRequest();
        request.setOperation(operation);
        request.setBucket("bucket-a");
        request.setSources(sources);
        request.setTargetFolder(targetFolder);
        request.setConflictPolicy(conflictPolicy);
        return request;
    }
}
