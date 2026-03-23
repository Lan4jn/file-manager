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

final class FileCommandService {

    private static final Logger log = LoggerFactory.getLogger(FileCommandService.class);

    private final MinioClient minioClient;
    private final FileDomainSupport support;

    FileCommandService(MinioClient minioClient, FileDomainSupport support) {
        this.minioClient = minioClient;
        this.support = support;
    }

    UploadResponse uploadFile(MultipartFile file, String folder, String bucket, String defaultBucket, String publicUrl) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = support.getFileExtension(originalFilename).toLowerCase();

            Set<String> allowedExtensions = support.getAllowedExtensions();
            if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
                throw new RuntimeException("不允许上传此类型文件: " + extension);
            }

            Long maxSize = support.getMaxFileSize();
            if (maxSize > 0 && file.getSize() > maxSize) {
                throw new RuntimeException("文件大小超出限制: 最大" + support.formatSize(maxSize));
            }

            String objectName = support.generateObjectName(folder, originalFilename, resolvedBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(resolvedBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(resolvedBucket).object(objectName).build());
            log.info("File uploaded successfully: {}", objectName);
            return support.buildUploadResponse(
                    objectName, originalFilename, file.getSize(), file.getContentType(), resolvedBucket, publicUrl, stat);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    void deleteFile(String objectName, String bucket, String defaultBucket) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            boolean isFolder = objectName.endsWith("/");
            if (isFolder) {
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(resolvedBucket)
                                .prefix(objectName)
                                .recursive(true)
                                .build()
                );
                for (Result<Item> result : results) {
                    Item item = result.get();
                    if (!item.isDir()) {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(resolvedBucket)
                                .object(item.objectName())
                                .build());
                    }
                }
            }

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(resolvedBucket)
                    .object(objectName)
                    .build());
            log.info("File deleted: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    void createFolder(String folderPath, String bucket, String defaultBucket, boolean createTestFile) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            support.createFolderObject(folderPath, resolvedBucket, createTestFile);
            log.info("Folder created: {}", folderPath);
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
        copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket, defaultBucket);
        deleteFile(sourceObjectName, sourceBucket, defaultBucket);
        log.info("File moved from {}/{} to {}/{}", sourceBucket, sourceObjectName, destBucket, destObjectName);
    }
}
