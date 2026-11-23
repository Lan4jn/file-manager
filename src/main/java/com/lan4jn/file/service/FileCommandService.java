package com.lan4jn.file.service;

import com.lan4jn.file.dto.UploadResponse;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

final class FileCommandService {

    private static final Logger log = LoggerFactory.getLogger(FileCommandService.class);
    private static final int UPLOAD_CONFIRM_MAX_ATTEMPTS = 10;
    private static final long UPLOAD_CONFIRM_RETRY_INTERVAL_MS = 500L;

    private final MinioClient minioClient;
    private final FileDomainSupport support;

    FileCommandService(MinioClient minioClient, FileDomainSupport support) {
        this.minioClient = minioClient;
        this.support = support;
    }

    UploadResponse uploadFile(MultipartFile file, String folder, String bucket, String defaultBucket, String publicUrl) {
        return uploadFile(file, folder, bucket, defaultBucket, publicUrl, null, null);
    }

    UploadResponse uploadFile(MultipartFile file,
                              String folder,
                              String bucket,
                              String defaultBucket,
                              String publicUrl,
                              String targetFileName,
                              String uploadTraceId) {
        long startedAt = System.currentTimeMillis();
        try {
            String requestedFileName = normalizeText(targetFileName) != null
                    ? normalizeText(targetFileName)
                    : file.getOriginalFilename();
            log.info("Application upload preparing, traceId: {}, originalFileName: {}, requestedFileName: {}, size: {}, contentType: {}, folder: {}, bucket: {}",
                    uploadTraceId, file.getOriginalFilename(), requestedFileName, file.getSize(), file.getContentType(), folder, bucket);
            FileDomainSupport.PreparedUpload preparedUpload =
                    support.prepareUpload(requestedFileName, file.getSize(), folder, bucket, defaultBucket);
            log.info("Application upload prepared, traceId: {}, resolvedBucket: {}, objectName: {}, fileName: {}",
                    uploadTraceId, preparedUpload.getBucket(), preparedUpload.getObjectName(), preparedUpload.getFileName());
            long putStartedAt = System.currentTimeMillis();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(preparedUpload.getBucket())
                            .object(preparedUpload.getObjectName())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Application upload putObject completed, traceId: {}, bucket: {}, objectName: {}, elapsedMs: {}",
                    uploadTraceId, preparedUpload.getBucket(), preparedUpload.getObjectName(), System.currentTimeMillis() - putStartedAt);

            StatObjectResponse stat = statUploadedObjectWithRetry(
                    preparedUpload.getBucket(), preparedUpload.getObjectName(), uploadTraceId, false);
            if (stat != null) {
                log.info("Application upload confirmed, traceId: {}, bucket: {}, objectName: {}, statSize: {}, statContentType: {}, totalElapsedMs: {}",
                        uploadTraceId, preparedUpload.getBucket(), preparedUpload.getObjectName(), stat.size(), stat.contentType(), System.currentTimeMillis() - startedAt);
            } else {
                log.warn("Application upload returned success without statObject confirmation, traceId: {}, bucket: {}, objectName: {}, totalElapsedMs: {}",
                        uploadTraceId, preparedUpload.getBucket(), preparedUpload.getObjectName(), System.currentTimeMillis() - startedAt);
            }
            return support.buildUploadResponse(
                    preparedUpload.getObjectName(),
                    preparedUpload.getFileName(),
                    file.getSize(),
                    file.getContentType(),
                    preparedUpload.getBucket(),
                    publicUrl,
                    stat);
        } catch (RuntimeException e) {
            log.error("Application upload failed, traceId: {}, originalFileName: {}, targetFileName: {}, folder: {}, bucket: {}, elapsedMs: {}, message: {}",
                    uploadTraceId, file.getOriginalFilename(), targetFileName, folder, bucket, System.currentTimeMillis() - startedAt, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Application upload failed, traceId: {}, originalFileName: {}, targetFileName: {}, folder: {}, bucket: {}, elapsedMs: {}, message: {}",
                    uploadTraceId, file.getOriginalFilename(), targetFileName, folder, bucket, System.currentTimeMillis() - startedAt, e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    FileDomainSupport.PreparedUpload prepareDirectUpload(String originalFilename,
                                                         Long size,
                                                         String folder,
                                                         String bucket,
                                                         String defaultBucket) {
        return support.prepareUpload(originalFilename, size, folder, bucket, defaultBucket);
    }

    UploadResponse completeDirectUpload(String bucket,
                                        String objectName,
                                        String fileName,
                                        String contentType,
                                        Long expectedSize,
                                        String publicUrl) {
        return completeDirectUpload(bucket, objectName, fileName, contentType, expectedSize, publicUrl, null);
    }

    UploadResponse completeDirectUpload(String bucket,
                                        String objectName,
                                        String fileName,
                                        String contentType,
                                        Long expectedSize,
                                        String publicUrl,
                                        String uploadTraceId) {
        long startedAt = System.currentTimeMillis();
        try {
            log.info("Direct upload confirmation started, traceId: {}, bucket: {}, objectName: {}, fileName: {}, expectedSize: {}, contentType: {}",
                    uploadTraceId, bucket, objectName, fileName, expectedSize, contentType);
            StatObjectResponse stat = statUploadedObjectWithRetry(bucket, objectName, uploadTraceId, true);
            if (expectedSize != null && expectedSize >= 0 && stat.size() != expectedSize) {
                log.error("Direct upload size mismatch, traceId: {}, bucket: {}, objectName: {}, expectedSize: {}, actualSize: {}",
                        uploadTraceId, bucket, objectName, expectedSize, stat.size());
                throw new RuntimeException("上传文件大小校验失败");
            }
            String resolvedContentType = stat.contentType() == null || stat.contentType().isBlank()
                    ? contentType
                    : stat.contentType();
            UploadResponse response = support.buildUploadResponse(
                    objectName,
                    fileName,
                    stat.size(),
                    resolvedContentType,
                    bucket,
                    publicUrl,
                    stat
            );
            log.info("Direct upload confirmation completed, traceId: {}, bucket: {}, objectName: {}, statSize: {}, statContentType: {}, elapsedMs: {}",
                    uploadTraceId, bucket, objectName, stat.size(), stat.contentType(), System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException e) {
            log.error("Direct upload confirmation failed, traceId: {}, bucket: {}, objectName: {}, fileName: {}, expectedSize: {}, elapsedMs: {}, message: {}",
                    uploadTraceId, bucket, objectName, fileName, expectedSize, System.currentTimeMillis() - startedAt, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Direct upload confirmation failed, traceId: {}, bucket: {}, objectName: {}, fileName: {}, expectedSize: {}, elapsedMs: {}, message: {}",
                    uploadTraceId, bucket, objectName, fileName, expectedSize, System.currentTimeMillis() - startedAt, e.getMessage(), e);
            throw new RuntimeException("确认上传失败: " + e.getMessage(), e);
        }
    }

    private StatObjectResponse statUploadedObjectWithRetry(String bucket,
                                                           String objectName,
                                                           String uploadTraceId,
                                                           boolean failWhenUnavailable) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= UPLOAD_CONFIRM_MAX_ATTEMPTS; attempt++) {
            try {
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder().bucket(bucket).object(objectName).build()
                );
                if (attempt > 1) {
                    log.info("Uploaded object visible after retry, traceId: {}, bucket: {}, objectName: {}, attempt: {}, statSize: {}, statContentType: {}",
                            uploadTraceId, bucket, objectName, attempt, stat.size(), stat.contentType());
                }
                return stat;
            } catch (Exception e) {
                lastException = e;
                if (!isObjectTemporarilyInvisible(e) || attempt >= UPLOAD_CONFIRM_MAX_ATTEMPTS) {
                    if (failWhenUnavailable) {
                        log.error("Uploaded object statObject failed, traceId: {}, bucket: {}, objectName: {}, attempt: {}, retryable: {}, message: {}",
                                uploadTraceId, bucket, objectName, attempt, isObjectTemporarilyInvisible(e), e.getMessage(), e);
                        throw e;
                    }
                    log.warn("Uploaded object statObject confirmation skipped after failure, traceId: {}, bucket: {}, objectName: {}, attempt: {}, retryable: {}, message: {}",
                            uploadTraceId, bucket, objectName, attempt, isObjectTemporarilyInvisible(e), e.getMessage(), e);
                    return null;
                }
                log.warn("Uploaded object not visible yet, retrying statObject, traceId: {}, bucket: {}, objectName: {}, attempt: {}/{}, message: {}",
                        uploadTraceId, bucket, objectName, attempt, UPLOAD_CONFIRM_MAX_ATTEMPTS, e.getMessage());
                sleepBeforeDirectUploadConfirmRetry();
            }
        }
        throw lastException;
    }

    private String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void sleepBeforeDirectUploadConfirmRetry() {
        try {
            Thread.sleep(UPLOAD_CONFIRM_RETRY_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("确认上传被中断", e);
        }
    }

    private boolean isObjectTemporarilyInvisible(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("object does not exist")
                        || normalized.contains("nosuchkey")
                        || normalized.contains("not found")
                        || normalized.contains("key does not exist")
                        || normalized.contains("对象不存在")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    void deleteFile(String objectName, String bucket, String defaultBucket) {
        deleteFiles(List.of(objectName), bucket, defaultBucket);
    }

    void deleteFiles(List<String> objectNames, String bucket, String defaultBucket) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            List<String> actualObjects = resolveDeleteTargets(objectNames, resolvedBucket);
            String rollbackPrefix = ".rollback/" + UUID.randomUUID() + "/";
            try {
                backupObjects(actualObjects, resolvedBucket, rollbackPrefix);
            } catch (Exception e) {
                cleanupRollbackObjects(actualObjects, resolvedBucket, rollbackPrefix);
                throw e;
            }
            try {
                for (String actualObject : actualObjects) {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(resolvedBucket)
                            .object(actualObject)
                            .build());
                }
            } catch (Exception e) {
                restoreObjects(actualObjects, resolvedBucket, rollbackPrefix);
                throw e;
            } finally {
                cleanupRollbackObjects(actualObjects, resolvedBucket, rollbackPrefix);
            }
            log.info("Files deleted: {}", actualObjects);
        } catch (Exception e) {
            log.error("Failed to delete files: {}", objectNames, e);
            throw new RuntimeException(
                    objectNames.size() > 1 ? "批量删除失败: " + e.getMessage() : "文件删除失败: " + e.getMessage(), e);
        }
    }

    void createFolder(String folderPath, String bucket, String defaultBucket, boolean createTestFile) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedPath = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        try {
            support.createFolderMarker(normalizedPath, resolvedBucket);
            if (createTestFile) {
                try {
                    support.createTestFile(normalizedPath, resolvedBucket);
                } catch (Exception e) {
                    support.deleteObjectQuietly(normalizedPath + "test.txt", resolvedBucket);
                    support.deleteObjectQuietly(normalizedPath, resolvedBucket);
                    throw e;
                }
            }
            log.info("Folder created: {}", normalizedPath);
        } catch (Exception e) {
            log.error("Failed to create folder: {}", folderPath, e);
            throw new RuntimeException("创建文件夹失败: " + e.getMessage(), e);
        }
    }

    void copyFile(String sourceObjectName, String destObjectName,
                  String sourceBucket, String destBucket, String defaultBucket) {
        String resolvedSourceBucket = support.resolveBucket(sourceBucket, defaultBucket);
        String resolvedDestBucket = support.resolveBucket(destBucket, defaultBucket);
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(resolvedDestBucket)
                            .object(destObjectName)
                            .source(CopySource.builder()
                                    .bucket(resolvedSourceBucket)
                                    .object(sourceObjectName)
                                    .build())
                            .build()
            );
            log.info("File copied from {}/{} to {}/{}",
                    resolvedSourceBucket, sourceObjectName, resolvedDestBucket, destObjectName);
        } catch (Exception e) {
            log.error("Failed to copy file", e);
            throw new RuntimeException("文件复制失败: " + e.getMessage(), e);
        }
    }

    void moveFile(String sourceObjectName, String destObjectName,
                  String sourceBucket, String destBucket, String defaultBucket) {
        String resolvedDestBucket = support.resolveBucket(destBucket, defaultBucket);
        copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket, defaultBucket);
        try {
            deleteFile(sourceObjectName, sourceBucket, defaultBucket);
            log.info("File moved from {}/{} to {}/{}", sourceBucket, sourceObjectName, destBucket, destObjectName);
        } catch (RuntimeException e) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(resolvedDestBucket)
                        .object(destObjectName)
                        .build());
            } catch (Exception rollbackError) {
                log.error("Failed to rollback moved target {}/{}", resolvedDestBucket, destObjectName, rollbackError);
            }
            throw new RuntimeException("移动失败: " + e.getMessage(), e);
        }
    }

    String rename(String sourceObjectName,
                  String targetName,
                  String bucket,
                  String defaultBucket,
                  String conflictPolicy) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedSource = normalizeRequiredText(sourceObjectName, "sourcePath不能为空");
        String normalizedTargetName = normalizeTargetName(targetName);
        boolean isFolder = normalizedSource.endsWith("/");
        String targetPath = buildRenameTargetPath(normalizedSource, normalizedTargetName, isFolder, resolvedBucket, conflictPolicy);
        if (isFolder) {
            renameFolder(normalizedSource, targetPath, resolvedBucket);
        } else {
            renameFile(normalizedSource, targetPath, resolvedBucket);
        }
        return targetPath;
    }

    private void renameFile(String sourceObjectName, String targetPath, String bucket) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(targetPath)
                            .source(CopySource.builder().bucket(bucket).object(sourceObjectName).build())
                            .build()
            );
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(sourceObjectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("重命名失败: " + e.getMessage(), e);
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeTargetName(String targetName) {
        String normalized = normalizeRequiredText(targetName, "targetName不能为空");
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("名称不合法，不能包含路径分隔符");
        }
        return normalized;
    }

    private void renameFolder(String sourcePrefix, String targetPrefix, String bucket) {
        try {
            List<String> sources = resolveDeleteTargets(List.of(sourcePrefix), bucket);
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("源目录不存在");
            }
            for (String source : sources) {
                String suffix = source.substring(sourcePrefix.length());
                String target = targetPrefix + suffix;
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(bucket)
                                .object(target)
                                .source(CopySource.builder().bucket(bucket).object(source).build())
                                .build()
                );
            }
            for (String source : sources) {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(source).build());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("重命名失败: " + e.getMessage(), e);
        }
    }

    private String buildRenameTargetPath(String sourceObjectName,
                                         String targetName,
                                         boolean isFolder,
                                         String bucket,
                                         String conflictPolicy) {
        int lastSlash = sourceObjectName.endsWith("/")
                ? sourceObjectName.substring(0, sourceObjectName.length() - 1).lastIndexOf('/')
                : sourceObjectName.lastIndexOf('/');
        String parent = lastSlash >= 0 ? sourceObjectName.substring(0, lastSlash + 1) : "";
        String targetPath = parent + targetName + (isFolder ? "/" : "");
        if (sourceObjectName.equals(targetPath)) {
            return targetPath;
        }
        if (!support.objectExists(bucket, targetPath)) {
            return targetPath;
        }
        if ("ASK".equalsIgnoreCase(String.valueOf(conflictPolicy))) {
            throw new IllegalStateException("目标名称已存在");
        }
        if ("RENAME".equalsIgnoreCase(String.valueOf(conflictPolicy))) {
            int counter = 2;
            String extension = isFolder ? "" : resolveExtension(targetName);
            String baseName = targetName;
            if (!isFolder && !extension.isEmpty()) {
                baseName = targetName.substring(0, targetName.length() - extension.length() - 1);
            }
            while (counter < 1000) {
                String candidateName = !isFolder && !extension.isEmpty()
                        ? baseName + "_" + counter + "." + extension
                        : baseName + "_" + counter;
                String candidatePath = parent + candidateName + (isFolder ? "/" : "");
                if (!support.objectExists(bucket, candidatePath)) {
                    return candidatePath;
                }
                counter++;
            }
        }
        deleteFile(targetPath, bucket, bucket);
        return targetPath;
    }

    private String resolveExtension(String targetName) {
        int dotIndex = targetName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= targetName.length() - 1) {
            return "";
        }
        return targetName.substring(dotIndex + 1);
    }

    private List<String> resolveDeleteTargets(List<String> objectNames, String bucket) throws Exception {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (String objectName : objectNames) {
            if (objectName.endsWith("/")) {
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(bucket)
                                .prefix(objectName)
                                .recursive(true)
                                .build()
                );
                for (Result<Item> result : results) {
                    Item item = result.get();
                    if (!item.isDir()) {
                        targets.add(item.objectName());
                    }
                }
                if (support.objectExists(bucket, objectName)) {
                    targets.add(objectName);
                }
            } else {
                targets.add(objectName);
            }
        }
        return new ArrayList<>(targets);
    }

    private void backupObjects(List<String> objectNames, String bucket, String rollbackPrefix) throws Exception {
        for (String objectName : objectNames) {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(rollbackPrefix + objectName)
                            .source(CopySource.builder().bucket(bucket).object(objectName).build())
                            .build()
            );
        }
    }

    private void restoreObjects(List<String> objectNames, String bucket, String rollbackPrefix) {
        for (String objectName : objectNames) {
            try {
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .source(CopySource.builder().bucket(bucket).object(rollbackPrefix + objectName).build())
                                .build()
                );
            } catch (Exception restoreError) {
                log.error("Failed to restore object {} from rollback backup", objectName, restoreError);
            }
        }
    }

    private void cleanupRollbackObjects(List<String> objectNames, String bucket, String rollbackPrefix) {
        for (String objectName : objectNames) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(rollbackPrefix + objectName)
                        .build());
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup rollback object {}", rollbackPrefix + objectName, cleanupError);
            }
        }
    }
}
