package com.lan4jn.file.service;

import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.repository.TransferArchiveTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferArchiveTaskServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateArchiveTaskAndMarkItCompleted() throws Exception {
        FileService fileService = mock(FileService.class);
        ConfigService configService = mock(ConfigService.class);
        TransferArchiveTaskRepository repository = mock(TransferArchiveTaskRepository.class);
        Map<Long, TransferArchiveTask> store = new LinkedHashMap<>();
        AtomicLong sequence = new AtomicLong(1L);

        when(fileService.resolveBatchArchiveEntries(List.of("docs/readme.txt"), "bucket-a"))
                .thenReturn(List.of(fileInfo("docs/readme.txt", "readme.txt", 5L)));
        when(configService.getArchiveTaskRetentionMinutes()).thenReturn(120);
        when(configService.getArchiveTaskMaxConcurrent()).thenReturn(1);

        doAnswer(invocation -> {
            TransferArchiveTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(sequence.getAndIncrement());
            }
            store.put(task.getId(), task);
            return task;
        }).when(repository).save(any(TransferArchiveTask.class));
        when(repository.findByIdAndOwnerUsername(1L, "admin"))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(1L)));
        when(repository.findByStatusOrderByCreateTimeAsc("PENDING"))
                .thenAnswer(invocation -> store.values().stream()
                        .filter(task -> "PENDING".equals(task.getStatus()))
                        .sorted(Comparator.comparing(TransferArchiveTask::getCreateTime))
                        .toList());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<FileInfo> files = invocation.getArgument(0, List.class);
            OutputStream outputStream = invocation.getArgument(2, OutputStream.class);
            for (FileInfo file : files) {
                outputStream.write((file.getObjectName() + "\n").getBytes());
            }
            return null;
        }).when(fileService).downloadResolvedFilesAsZip(any(), eq("bucket-a"), any(OutputStream.class));

        TransferArchiveTaskService service = new TransferArchiveTaskService(
                repository, fileService, configService, tempDir.toString());

        Long taskId = service.createBatchArchiveTask("admin", "bucket-a", List.of("docs/readme.txt"));
        TransferArchiveTask task = service.getTask(taskId, "admin");

        assertEquals("PENDING", task.getStatus());

        service.runPendingTasks();

        TransferArchiveTask completed = service.getTask(taskId, "admin");
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("READY", completed.getStage());
        assertNotNull(completed.getArchiveLocation());
        assertFalse(completed.getArchiveLocation().isBlank());
        assertNotNull(completed.getFinishTime());
        assertEquals(1, completed.getFileCountProcessed());
        assertEquals(120, (completed.getExpireTime() - completed.getFinishTime()) / 60000);
        assertTrue(Files.exists(Path.of(completed.getArchiveLocation())));
    }

    private FileInfo fileInfo(String objectName, String fileName, long size) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName(objectName);
        fileInfo.setFileName(fileName);
        fileInfo.setSize(size);
        fileInfo.setIsFolder(false);
        return fileInfo;
    }
}
