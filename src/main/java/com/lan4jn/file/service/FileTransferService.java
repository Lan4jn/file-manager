package com.lan4jn.file.service;

import com.lan4jn.file.dto.FileTransferExecuteRequest;
import com.lan4jn.file.dto.FileTransferExecuteResponse;
import com.lan4jn.file.dto.FileTransferPreviewRequest;
import com.lan4jn.file.dto.FileTransferPreviewResponse;
import com.lan4jn.file.model.FileInfo;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileTransferService {

    private final FileDomainSupport support;
    private final FileCommandService commandService;
    private final FileQueryService queryService;

    @Value("${minio.default-bucket:file-manager}")
    private String defaultBucket;

    @Autowired
    public FileTransferService(MinioClient minioClient, ConfigService configService) {
        this.support = new FileDomainSupport(minioClient, configService);
        this.commandService = new FileCommandService(minioClient, support);
        this.queryService = new FileQueryService(minioClient, support);
    }

    FileTransferService(FileDomainSupport support, FileCommandService commandService, FileQueryService queryService) {
        this.support = support;
        this.commandService = commandService;
        this.queryService = queryService;
        this.defaultBucket = "file-manager";
    }

    public FileTransferPreviewResponse preview(FileTransferPreviewRequest request) {
        TransferContext context = buildContext(request);
        FileTransferPreviewResponse response = new FileTransferPreviewResponse();
        response.setValid(true);
        response.setTargetFolder(context.targetFolder());
        List<FileTransferPreviewResponse.Item> items = new ArrayList<>();
        for (String source : context.sources()) {
            FileTransferPreviewResponse.Item item = previewItem(context, source);
            items.add(item);
            if (item.getMessage() != null && !item.getMessage().isEmpty()) {
                response.setValid(false);
                response.setMessage(item.getMessage());
            }
            if (item.isConflict()) {
                response.setHasConflicts(true);
            }
        }
        response.setItems(items);
        if (response.getMessage() == null && response.isHasConflicts()) {
            response.setMessage("目标目录存在同名文件或目录");
        }
        return response;
    }

    public FileTransferExecuteResponse execute(FileTransferExecuteRequest request) {
        TransferContext context = buildContext(request);
        FileTransferPreviewResponse preview = preview(request);
        if (!preview.isValid()) {
            throw new IllegalArgumentException(preview.getMessage());
        }
        FileTransferExecuteResponse response = new FileTransferExecuteResponse();
        List<String> resolvedTargets = new ArrayList<>();
        List<ExecutedTransfer> completedTransfers = new ArrayList<>();
        for (FileTransferPreviewResponse.Item item : preview.getItems()) {
            TransferBackup backup = backupTarget(context.bucket(), item.getTarget(), item.isFolder());
            try {
                executeSingle(context, item.getSource(), item.getTarget(), item.isFolder());
                resolvedTargets.add(item.getTarget());
                completedTransfers.add(new ExecutedTransfer(item.getSource(), item.getTarget(), item.isFolder(), backup));
            } catch (RuntimeException ex) {
                rollbackTransfer(context, item.getSource(), item.getTarget(), item.isFolder(), backup);
                rollbackCompletedTransfers(context, completedTransfers);
                throw new RuntimeException("文件转移失败: " + ex.getMessage(), ex);
            }
        }
        cleanupBackups(context.bucket(), completedTransfers);
        response.setResolvedTargets(resolvedTargets);
        response.setSuccessCount(resolvedTargets.size());
        return response;
    }

    private FileTransferPreviewResponse.Item previewItem(TransferContext context, String source) {
        FileTransferPreviewResponse.Item item = new FileTransferPreviewResponse.Item();
        item.setSource(source);
        item.setFolder(isFolder(source));
        String invalidMessage = validateTarget(context.targetFolder(), source, context.operation());
        if (invalidMessage != null) {
            item.setMessage(invalidMessage);
            item.setTarget(resolveTargetPath(context, source, false));
            return item;
        }
        String missingMessage = validateExistence(context.bucket(), context.targetFolder(), source, item.isFolder());
        if (missingMessage != null) {
            item.setMessage(missingMessage);
            item.setTarget(resolveTargetPath(context, source, false));
            return item;
        }
        boolean renameOnConflict = "RENAME".equals(context.conflictPolicy());
        String target = resolveTargetPath(context, source, renameOnConflict);
        item.setTarget(target);
        if (!renameOnConflict && exists(context.bucket(), target, item.isFolder())) {
            item.setConflict(true);
        }
        return item;
    }

    private void executeSingle(TransferContext context, String source, String target, boolean folder) {
        if (folder) {
            executeFolder(context, source, target);
            return;
        }
        if ("MOVE".equals(context.operation())) {
            commandService.moveFile(source, target, context.bucket(), context.bucket(), defaultBucket);
            return;
        }
        commandService.copyFile(source, target, context.bucket(), context.bucket(), defaultBucket);
    }

    private void executeFolder(TransferContext context, String source, String targetFolderRoot) {
        List<FileInfo> entries = queryService.listFilesRecursive(source, context.bucket(), defaultBucket);
        if ("OVERWRITE".equals(context.conflictPolicy()) && exists(context.bucket(), targetFolderRoot, true)) {
            commandService.deleteFile(targetFolderRoot, context.bucket(), defaultBucket);
        }
        commandService.createFolder(targetFolderRoot, context.bucket(), defaultBucket, false);
        for (FileInfo entry : entries) {
            String relativePath = entry.getObjectName().substring(source.length());
            String targetObject = targetFolderRoot + relativePath;
            commandService.copyFile(entry.getObjectName(), targetObject, context.bucket(), context.bucket(), defaultBucket);
        }
        if ("MOVE".equals(context.operation())) {
            commandService.deleteFile(source, context.bucket(), defaultBucket);
        }
    }

    private String resolveTargetPath(TransferContext context, String source, boolean renameOnConflict) {
        String sourceName = sourceBaseName(source);
        String target = context.targetFolder() + sourceName;
        if (!renameOnConflict) {
            return target;
        }
        return nextAvailableTarget(context.bucket(), target, isFolder(source));
    }

    private String nextAvailableTarget(String bucket, String target, boolean folder) {
        if (!exists(bucket, target, folder)) {
            return target;
        }
        String baseName = sourceBaseName(target);
        String parent = parentFolder(target);
        String extension = folder ? "" : extension(baseName);
        String nameOnly = folder ? trimFolderSuffix(baseName) : stripExtension(baseName);
        int counter = 1;
        while (counter < 1000) {
            String candidateName = nameOnly + "(" + counter + ")" + extension;
            String candidate = parent + candidateName + (folder ? "/" : "");
            if (!exists(bucket, candidate, folder)) {
                return candidate;
            }
            counter++;
        }
        throw new IllegalStateException("无法为目标生成共存名称");
    }

    private String validateTarget(String targetFolder, String source, String operation) {
        String normalizedSourceFolder = isFolder(source) ? source : parentFolder(source);
        if ("MOVE".equals(operation) && normalizedSourceFolder.equals(targetFolder)) {
            return "不能移动到原目录";
        }
        if (isFolder(source) && targetFolder.startsWith(source)) {
            return "不能移动或复制到自身子目录";
        }
        return null;
    }

    private String validateExistence(String bucket, String targetFolder, String source, boolean folder) {
        if (!sourceExists(bucket, source, folder)) {
            return folder ? "源目录不存在: " + source : "源文件不存在: " + source;
        }
        if (!targetFolderExists(bucket, targetFolder)) {
            return "目标目录不存在: " + targetFolder;
        }
        return null;
    }

    private boolean exists(String bucket, String objectName, boolean folder) {
        if (support.objectExists(bucket, objectName)) {
            return true;
        }
        return folder && !queryService.listFilesRecursive(objectName, bucket, defaultBucket).isEmpty();
    }

    private boolean sourceExists(String bucket, String source, boolean folder) {
        return exists(bucket, source, folder);
    }

    private boolean targetFolderExists(String bucket, String targetFolder) {
        return targetFolder == null || targetFolder.isEmpty() || exists(bucket, targetFolder, true);
    }

    private TransferBackup backupTarget(String bucket, String target, boolean folder) {
        if (!exists(bucket, target, folder)) {
            return TransferBackup.none();
        }
        String backupRoot = ".transfer-backup/" + UUID.randomUUID() + "/";
        boolean folderMarkerExists = folder && support.objectExists(bucket, target);
        if (folder && folderMarkerExists) {
            commandService.createFolder(backupRoot, bucket, defaultBucket, false);
        }
        if (!folder) {
            String backupObject = backupRoot + "item";
            commandService.copyFile(target, backupObject, bucket, bucket, defaultBucket);
            return TransferBackup.fileBackup(backupObject);
        }
        List<FileInfo> entries = queryService.listFilesRecursive(target, bucket, defaultBucket);
        for (FileInfo entry : entries) {
            String relativePath = entry.getObjectName().substring(target.length());
            commandService.copyFile(entry.getObjectName(), backupRoot + relativePath, bucket, bucket, defaultBucket);
        }
        return TransferBackup.folderBackup(backupRoot, folderMarkerExists);
    }

    private void rollbackCompletedTransfers(TransferContext context, List<ExecutedTransfer> completedTransfers) {
        for (int i = completedTransfers.size() - 1; i >= 0; i--) {
            ExecutedTransfer transfer = completedTransfers.get(i);
            rollbackTransfer(context, transfer.source(), transfer.target(), transfer.folder(), transfer.backup());
        }
    }

    private void rollbackTransfer(TransferContext context, String source, String target, boolean folder, TransferBackup backup) {
        try {
            if ("MOVE".equals(context.operation())) {
                rollbackMovedTarget(context.bucket(), source, target, folder);
            } else {
                commandService.deleteFile(target, context.bucket(), defaultBucket);
            }
        } finally {
            restoreBackup(context.bucket(), target, folder, backup);
            cleanupBackup(context.bucket(), backup);
        }
    }

    private void rollbackMovedTarget(String bucket, String source, String target, boolean folder) {
        if (!exists(bucket, target, folder)) {
            return;
        }
        if (!folder) {
            commandService.moveFile(target, source, bucket, bucket, defaultBucket);
            return;
        }
        List<FileInfo> targetEntries = queryService.listFilesRecursive(target, bucket, defaultBucket);
        commandService.createFolder(source, bucket, defaultBucket, false);
        for (FileInfo entry : targetEntries) {
            String relativePath = entry.getObjectName().substring(target.length());
            commandService.copyFile(entry.getObjectName(), source + relativePath, bucket, bucket, defaultBucket);
        }
        commandService.deleteFile(target, bucket, defaultBucket);
    }

    private void restoreBackup(String bucket, String target, boolean folder, TransferBackup backup) {
        if (backup == null || !backup.exists()) {
            return;
        }
        if (folder) {
            if (backup.folderMarkerExists()) {
                commandService.createFolder(target, bucket, defaultBucket, false);
            }
            List<FileInfo> entries = queryService.listFilesRecursive(backup.backupRoot(), bucket, defaultBucket);
            for (FileInfo entry : entries) {
                String relativePath = entry.getObjectName().substring(backup.backupRoot().length());
                commandService.copyFile(entry.getObjectName(), target + relativePath, bucket, bucket, defaultBucket);
            }
            return;
        }
        commandService.copyFile(backup.backupObject(), target, bucket, bucket, defaultBucket);
    }

    private void cleanupBackups(String bucket, List<ExecutedTransfer> completedTransfers) {
        for (ExecutedTransfer transfer : completedTransfers) {
            cleanupBackup(bucket, transfer.backup());
        }
    }

    private void cleanupBackup(String bucket, TransferBackup backup) {
        if (backup == null || !backup.exists()) {
            return;
        }
        String resolvedBucket = bucket == null ? null : bucket;
        if (backup.folder()) {
            commandService.deleteFile(backup.backupRoot(), resolvedBucket, defaultBucket);
            return;
        }
        commandService.deleteFile(backup.backupObject(), resolvedBucket, defaultBucket);
    }

    private TransferContext buildContext(FileTransferPreviewRequest request) {
        if (request == null || request.getSources() == null || request.getSources().isEmpty()) {
            throw new IllegalArgumentException("sources不能为空");
        }
        String operation = normalizeOperation(request.getOperation());
        String conflictPolicy = normalizeConflictPolicy(request.getConflictPolicy());
        String bucket = support.resolveBucket(request.getBucket(), defaultBucket);
        String targetFolder = support.normalizeFolder(request.getTargetFolder() == null ? "" : request.getTargetFolder().trim());
        List<String> sources = request.getSources().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources不能为空");
        }
        return new TransferContext(operation, bucket, sources, targetFolder, conflictPolicy);
    }

    private String normalizeOperation(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"MOVE".equals(normalized) && !"COPY".equals(normalized)) {
            throw new IllegalArgumentException("operation不支持");
        }
        return normalized;
    }

    private String normalizeConflictPolicy(String raw) {
        String normalized = raw == null ? "ASK" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"ASK".equals(normalized) && !"OVERWRITE".equals(normalized) && !"RENAME".equals(normalized)) {
            throw new IllegalArgumentException("conflictPolicy不支持");
        }
        return normalized;
    }

    private boolean isFolder(String objectName) {
        return objectName != null && objectName.endsWith("/");
    }

    private String sourceBaseName(String objectName) {
        String normalized = objectName;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) + (isFolder(objectName) ? "/" : "") : objectName;
    }

    private String parentFolder(String objectName) {
        String normalized = objectName == null ? "" : objectName;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(0, slash + 1) : "";
    }

    private String trimFolderSuffix(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private record TransferContext(String operation, String bucket, List<String> sources, String targetFolder,
                                   String conflictPolicy) {
    }

    private record TransferBackup(boolean exists, boolean folder, String backupRoot, String backupObject,
                                  boolean folderMarkerExists) {

        private static TransferBackup none() {
            return new TransferBackup(false, false, null, null, false);
        }

        private static TransferBackup fileBackup(String backupObject) {
            return new TransferBackup(true, false, null, backupObject, false);
        }

        private static TransferBackup folderBackup(String backupRoot, boolean folderMarkerExists) {
            return new TransferBackup(true, true, backupRoot, null, folderMarkerExists);
        }
    }

    private record ExecutedTransfer(String source, String target, boolean folder, TransferBackup backup) {
    }
}
