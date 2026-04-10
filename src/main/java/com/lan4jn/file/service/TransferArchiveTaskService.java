package com.lan4jn.file.service;

import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.repository.TransferArchiveTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TransferArchiveTaskService {

    private static final Logger log = LoggerFactory.getLogger(TransferArchiveTaskService.class);
    private static final String TASK_TYPE_BATCH = "BATCH";
    private static final String TASK_TYPE_FOLDER = "FOLDER";
    private static final String TASK_TYPE_SHARE = "SHARE";
    private static final String SCENE_FILE = "FILE";
    private static final String SCENE_SHARE = "SHARE";
    private static final String SHARE_TASK_OWNER = "__share__";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STAGE_QUEUED = "QUEUED";
    private static final String STAGE_ARCHIVING = "ARCHIVING";
    private static final String STAGE_READY = "READY";
    private static final String STAGE_FAILED = "FAILED";

    private final TransferArchiveTaskRepository repository;
    private final FileService fileService;
    private final ConfigService configService;
    private final ShareService shareService;
    private final String archiveBaseDir;
    private final ConcurrentMap<Long, ShareArchivePlan> shareArchivePlans = new ConcurrentHashMap<>();
    private final AtomicBoolean archiveRunnerActive = new AtomicBoolean(false);

    public TransferArchiveTaskService(TransferArchiveTaskRepository repository,
                                      FileService fileService,
                                      ConfigService configService,
                                      ShareService shareService,
                                      @Value("${transfer.archive.base-dir:data/transfer-archives}") String archiveBaseDir) {
        this.repository = repository;
        this.fileService = fileService;
        this.configService = configService;
        this.shareService = shareService;
        this.archiveBaseDir = archiveBaseDir;
    }

    @Transactional
    public Long createTask(String username, TransferArchiveTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String scene = normalizeUpper(request.getScene(), SCENE_FILE);
        if (SCENE_SHARE.equals(scene)) {
            return createShareArchiveTask(request.getShareCode(), request.getPassword());
        }

        String taskType = normalizeUpper(request.getTaskType(), "");
        if (TASK_TYPE_BATCH.equals(taskType)) {
            return createBatchArchiveTask(username, request.getBucket(), requireTextList(request.getObjectNames(), "objectNames"));
        }
        if (TASK_TYPE_FOLDER.equals(taskType)) {
            return createFolderArchiveTask(username, request.getBucket(), requireText(request.getObjectName(), "objectName"));
        }
        throw new IllegalArgumentException("不支持的归档任务类型");
    }

    @Transactional
    public Long createBatchArchiveTask(String username, String bucket, List<String> objectNames) {
        List<String> normalizedObjectNames = requireTextList(objectNames, "objectNames");
        String resolvedBucket = resolveBucket(bucket);
        List<FileInfo> files = fileService.resolveBatchArchiveEntries(normalizedObjectNames, resolvedBucket);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("未找到可归档的文件");
        }
        TransferArchiveTask task = new TransferArchiveTask();
        long now = System.currentTimeMillis();
        task.setTaskType(TASK_TYPE_BATCH);
        task.setScene(SCENE_FILE);
        task.setStatus(STATUS_PENDING);
        task.setStage(STAGE_QUEUED);
        task.setOwnerUsername(requireText(username, "username"));
        task.setBucketName(resolvedBucket);
        task.setSourceDescriptor(String.join("\n", normalizedObjectNames));
        task.setFileCountTotal(files.size());
        task.setFileCountProcessed(0);
        task.setBytesWritten(0L);
        task.setArchiveFileName("batch-download-" + files.size() + ".zip");
        task.setCreateTime(now);
        return repository.save(task).getId();
    }

    @Transactional
    public Long createFolderArchiveTask(String username, String bucket, String objectName) {
        String normalizedObjectName = requireText(objectName, "objectName");
        String resolvedBucket = resolveBucket(bucket);
        List<FileInfo> files = fileService.resolveFolderArchiveEntries(normalizedObjectName, resolvedBucket);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("目录下没有可归档的文件");
        }
        TransferArchiveTask task = new TransferArchiveTask();
        long now = System.currentTimeMillis();
        task.setTaskType(TASK_TYPE_FOLDER);
        task.setScene(SCENE_FILE);
        task.setStatus(STATUS_PENDING);
        task.setStage(STAGE_QUEUED);
        task.setOwnerUsername(requireText(username, "username"));
        task.setBucketName(resolvedBucket);
        task.setSourceDescriptor(normalizedObjectName);
        task.setFileCountTotal(files.size());
        task.setFileCountProcessed(0);
        task.setBytesWritten(0L);
        task.setArchiveFileName(buildFolderArchiveName(normalizedObjectName));
        task.setCreateTime(now);
        return repository.save(task).getId();
    }

    @Transactional
    public Long createShareArchiveTask(String shareCode, String password) {
        ShareArchivePlan plan = requireShareService().prepareShareArchive(
                requireText(shareCode, "shareCode"),
                nullableText(password)
        );
        TransferArchiveTask task = new TransferArchiveTask();
        long now = System.currentTimeMillis();
        task.setTaskType(TASK_TYPE_SHARE);
        task.setScene(SCENE_SHARE);
        task.setStatus(STATUS_PENDING);
        task.setStage(STAGE_QUEUED);
        task.setOwnerUsername(SHARE_TASK_OWNER);
        task.setBucketName(plan.getShareLink().getBucket());
        task.setSourceDescriptor(plan.getShareLink().getObjectName());
        task.setShareCode(plan.getShareLink().getShareCode());
        task.setFileCountTotal(plan.getEntries().size());
        task.setFileCountProcessed(0);
        task.setBytesWritten(0L);
        task.setArchiveFileName(requireShareService().getShareArchiveName(
                plan.getShareLink().getShareCode(),
                nullableText(password)
        ));
        task.setCreateTime(now);
        TransferArchiveTask saved = repository.save(task);
        shareArchivePlans.put(saved.getId(), plan);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public TransferArchiveTask getTask(Long taskId, String username) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        return repository.findByIdAndOwnerUsername(taskId, requireText(username, "username"))
                .orElseThrow(() -> new IllegalArgumentException("归档任务不存在"));
    }

    @Transactional(readOnly = true)
    public TransferArchiveTask getShareTask(Long taskId, String shareCode, String password) {
        String resolvedShareCode = requireText(shareCode, "shareCode");
        requireShareService().peekShareInfo(resolvedShareCode, nullableText(password));
        TransferArchiveTask task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("归档任务不存在"));
        if (!SCENE_SHARE.equals(normalizeUpper(task.getScene(), SCENE_FILE))
                || !resolvedShareCode.equals(task.getShareCode())) {
            throw new IllegalArgumentException("归档任务不存在");
        }
        return task;
    }

    @Transactional
    public void runPendingTasks() {
        while (true) {
            List<TransferArchiveTask> pendingTasks = repository.findByStatusOrderByCreateTimeAsc(STATUS_PENDING);
            if (pendingTasks.isEmpty()) {
                return;
            }
            int maxConcurrent = configService.getArchiveTaskMaxConcurrent();
            int limit = Math.min(pendingTasks.size(), Math.max(1, maxConcurrent));
            for (int i = 0; i < limit; i++) {
                runSingleTask(pendingTasks.get(i));
            }
        }
    }

    public void triggerPendingTasksAsync() {
        if (!archiveRunnerActive.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                runPendingTasks();
            } finally {
                archiveRunnerActive.set(false);
                if (!repository.findByStatusOrderByCreateTimeAsc(STATUS_PENDING).isEmpty()) {
                    triggerPendingTasksAsync();
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public ArchiveDownload openArchiveDownload(Long taskId, String username, String shareCode, String password) {
        TransferArchiveTask task = shareCode == null || shareCode.trim().isEmpty()
                ? getTask(taskId, requireText(username, "username"))
                : getShareTask(taskId, shareCode, password);
        ensureDownloadable(task);
        Path archivePath = Path.of(task.getArchiveLocation());
        if (!Files.exists(archivePath)) {
            throw new IllegalArgumentException("归档文件不存在");
        }
        try {
            return new ArchiveDownload(
                    task.getArchiveFileName(),
                    Files.size(archivePath),
                    Files.newInputStream(archivePath)
            );
        } catch (Exception ex) {
            throw new RuntimeException("打开归档文件失败: " + ex.getMessage(), ex);
        }
    }

    private void runSingleTask(TransferArchiveTask task) {
        task.setStatus(STATUS_RUNNING);
        task.setStage(STAGE_ARCHIVING);
        repository.save(task);

        try {
            Path archivePath = prepareArchivePath(task);
            Files.createDirectories(archivePath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(archivePath)) {
                int processedCount = writeArchive(task, outputStream);
                task.setFileCountProcessed(processedCount);
            }

            long now = System.currentTimeMillis();
            task.setStatus(STATUS_COMPLETED);
            task.setStage(STAGE_READY);
            task.setArchiveLocation(archivePath.toAbsolutePath().toString());
            task.setBytesWritten(Files.size(archivePath));
            task.setFinishTime(now);
            task.setExpireTime(now + configService.getArchiveTaskRetentionMinutes() * 60_000L);
            task.setErrorMessage(null);
            repository.save(task);
        } catch (Exception ex) {
            task.setStatus(STATUS_FAILED);
            task.setStage(STAGE_FAILED);
            task.setFinishTime(System.currentTimeMillis());
            task.setErrorMessage(ex.getMessage());
            repository.save(task);
            log.error("归档任务执行失败 taskId={}", task.getId(), ex);
        } finally {
            if (SCENE_SHARE.equals(normalizeUpper(task.getScene(), SCENE_FILE))) {
                shareArchivePlans.remove(task.getId());
            }
        }
    }

    private int writeArchive(TransferArchiveTask task, OutputStream outputStream) throws Exception {
        if (SCENE_SHARE.equals(normalizeUpper(task.getScene(), SCENE_FILE))) {
            ShareArchivePlan plan = resolveSharePlan(task);
            requireShareService().downloadPreparedShareAsZip(plan, outputStream);
            return plan.getEntries().size();
        }

        List<FileInfo> files = resolveTaskFiles(task);
        fileService.downloadResolvedFilesAsZip(files, task.getBucketName(), outputStream);
        return files.size();
    }

    private List<FileInfo> resolveTaskFiles(TransferArchiveTask task) {
        if (TASK_TYPE_BATCH.equals(task.getTaskType())) {
            return fileService.resolveBatchArchiveEntries(parseDescriptorLines(task.getSourceDescriptor()), task.getBucketName());
        }
        if (TASK_TYPE_FOLDER.equals(task.getTaskType())) {
            return fileService.resolveFolderArchiveEntries(task.getSourceDescriptor(), task.getBucketName());
        }
        throw new IllegalArgumentException("不支持的归档任务类型");
    }

    private ShareArchivePlan resolveSharePlan(TransferArchiveTask task) {
        ShareArchivePlan cachedPlan = shareArchivePlans.get(task.getId());
        if (cachedPlan != null) {
            return cachedPlan;
        }
        return requireShareService().prepareShareArchive(task.getShareCode(), null);
    }

    private Path prepareArchivePath(TransferArchiveTask task) {
        String safeFileName = sanitizeFileName(task.getArchiveFileName());
        return Paths.get(archiveBaseDir).resolve(task.getId() + "-" + safeFileName);
    }

    private String buildFolderArchiveName(String objectName) {
        String normalized = objectName;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        String folderName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        if (folderName.isBlank()) {
            folderName = "folder";
        }
        return folderName + ".zip";
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private List<String> parseDescriptorLines(String descriptor) {
        List<String> lines = new ArrayList<>();
        if (descriptor == null || descriptor.isBlank()) {
            return lines;
        }
        for (String line : descriptor.split("\\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String resolveBucket(String bucket) {
        if (bucket == null || bucket.trim().isEmpty()) {
            return fileService.getDefaultBucket();
        }
        return bucket.trim();
    }

    private String normalizeUpper(String value, String defaultValue) {
        String text = value == null || value.trim().isEmpty() ? defaultValue : value.trim();
        return text.toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private List<String> requireTextList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                normalized.add(value.trim());
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String nullableText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private ShareService requireShareService() {
        if (shareService == null) {
            throw new IllegalStateException("分享归档能力未初始化");
        }
        return shareService;
    }

    private void ensureDownloadable(TransferArchiveTask task) {
        if (!STATUS_COMPLETED.equals(task.getStatus()) || !STAGE_READY.equals(task.getStage())) {
            throw new IllegalArgumentException("归档任务尚未完成");
        }
        if (task.getArchiveLocation() == null || task.getArchiveLocation().isBlank()) {
            throw new IllegalArgumentException("归档文件不存在");
        }
        if (task.getExpireTime() != null && task.getExpireTime() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("归档文件已过期");
        }
    }

    public record ArchiveDownload(String fileName, Long contentLength, InputStream inputStream) {
    }
}
