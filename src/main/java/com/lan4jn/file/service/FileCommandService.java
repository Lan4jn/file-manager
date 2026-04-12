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

    private final MinioClient minioClient;
    private final FileDomainSupport support;

    FileCommandService(MinioClient minioClient, FileDomainSupport support) {
        this.minioClient = minioClient;
        this.support = support;
    }

    UploadResponse uploadFile(MultipartFile file, String folder, String bucket, String defaultBucket, String publicUrl) {
        try {
            FileDomainSupport.PreparedUpload preparedUpload =
                    support.prepareUpload(file.getOriginalFilename(), file.getSize(), folder, bucket, defaultBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(preparedUpload.getBucket())
                            .object(preparedUpload.getObjectName())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(preparedUpload.getBucket()).object(preparedUpload.getObjectName()).build());
            log.info("File uploaded successfully: {}", preparedUpload.getObjectName());
            return support.buildUploadResponse(
                    preparedUpload.getObjectName(),
                    preparedUpload.getFileName(),
                    file.getSize(),
                    file.getContentType(),
                    preparedUpload.getBucket(),
                    publicUrl,
                    stat);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
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
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectName).build()
            );
            if (expectedSize != null && expectedSize >= 0 && stat.size() != expectedSize) {
                throw new RuntimeException("上传文件大小校验失败");
            }
            String resolvedContentType = stat.contentType() == null || stat.contentType().isBlank()
                    ? contentType
                    : stat.contentType();
            return support.buildUploadResponse(
                    objectName,
                    fileName,
                    stat.size(),
                    resolvedContentType,
                    bucket,
                    publicUrl,
                    stat
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to complete direct upload: {}/{}", bucket, objectName, e);
            throw new RuntimeException("确认上传失败: " + e.getMessage(), e);
        }
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
