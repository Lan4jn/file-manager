package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.FileInfo;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;

final class FileDomainSupport {

    private final MinioClient minioClient;
    private final ConfigService configService;

    FileDomainSupport(MinioClient minioClient, ConfigService configService) {
        this.minioClient = minioClient;
        this.configService = configService;
    }

    String resolveBucket(String bucket, String defaultBucket) {
        return StrUtil.isBlank(bucket) ? defaultBucket : bucket;
    }

    String normalizeFolder(String folder) {
        if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
            return folder + "/";
        }
        return folder;
    }

    FileInfo buildFileInfo(Item item, String bucket, String objectName, boolean isFolder, String publicUrl) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName(objectName);
        fileInfo.setFileName(getFileName(objectName));
        fileInfo.setSize(item.size());
        fileInfo.setIsFolder(isFolder);
        try {
            if (item.lastModified() != null) {
                fileInfo.setUploadTime(LocalDateTime.ofInstant(
                        item.lastModified().toInstant(), ZoneId.systemDefault()));
            } else {
                fileInfo.setUploadTime(LocalDateTime.now());
            }
        } catch (Exception e) {
            fileInfo.setUploadTime(LocalDateTime.now());
        }
        fileInfo.setUrl(publicUrl + "/" + bucket + "/" + objectName);
        return fileInfo;
    }

    FileInfo buildFileInfo(StatObjectResponse stat, String objectName, String bucket, String publicUrl) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName(objectName);
        fileInfo.setFileName(getFileName(objectName));
        fileInfo.setSize(stat.size());
        fileInfo.setContentType(stat.contentType());
        fileInfo.setEtag(stat.etag());
        fileInfo.setUploadTime(LocalDateTime.ofInstant(
                stat.lastModified().toInstant(), ZoneId.systemDefault()));
        fileInfo.setUrl(publicUrl + "/" + bucket + "/" + objectName);
        fileInfo.setIsFolder(objectName.endsWith("/"));
        return fileInfo;
    }

    UploadResponse buildUploadResponse(String objectName, String originalFilename, long size, String contentType,
                                       String bucket, String publicUrl, StatObjectResponse stat) {
        UploadResponse response = new UploadResponse();
        response.setObjectName(objectName);
        response.setFileName(originalFilename);
        response.setSize(size);
        response.setContentType(contentType);
        response.setUrl(publicUrl + "/" + bucket + "/" + objectName);
        response.setEtag(stat.etag());
        return response;
    }

    InputStream getFileStream(String objectName, String bucket, String defaultBucket) {
        return getFileStream(objectName, bucket, defaultBucket, null, null);
    }

    InputStream getFileStream(String objectName, String bucket, String defaultBucket, Long offset, Long length) {
        String resolvedBucket = resolveBucket(bucket, defaultBucket);
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(resolvedBucket)
                    .object(objectName);
            if (offset != null) {
                builder.offset(offset);
            }
            if (length != null) {
                builder.length(length);
            }
            return minioClient.getObject(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("获取文件失败: " + e.getMessage(), e);
        }
    }

    String generateObjectName(String folder, String originalFilename, String bucket) {
        String extension = getFileExtension(originalFilename);
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotEmpty(folder)) {
            if (!folder.endsWith("/")) {
                sb.append(folder).append("/");
            } else {
                sb.append(folder);
            }
        }

        String nameWithoutExt;
        if (StrUtil.isNotEmpty(extension)) {
            nameWithoutExt = originalFilename.substring(
                    0, Math.max(0, originalFilename.length() - extension.length() - 1));
        } else {
            nameWithoutExt = originalFilename;
        }
        nameWithoutExt = nameWithoutExt.replaceAll("[\\\\/:*?\"<>|]", "_");

        if (StrUtil.isNotEmpty(extension)) {
            sb.append(nameWithoutExt).append(".").append(extension);
        } else {
            sb.append(nameWithoutExt);
        }

        String objectName = sb.toString();
        if (fileExists(objectName, bucket)) {
            int counter = 2;
            String baseName = nameWithoutExt;
            while (true) {
                String newObjectName;
                if (StrUtil.isNotEmpty(extension)) {
                    newObjectName = sb.toString().replace(
                            baseName + "." + extension, baseName + "_" + counter + "." + extension);
                } else {
                    newObjectName = sb.toString().replace(baseName, baseName + "_" + counter);
                }
                if (!fileExists(newObjectName, bucket)) {
                    return newObjectName;
                }
                counter++;
                if (counter > 1000) {
                    throw new RuntimeException("文件命名冲突，无法创建唯一文件名");
                }
            }
        }

        return objectName;
    }

    void createFolderObject(String folderPath, String bucket, boolean createTestFile) throws Exception {
        String normalizedPath = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        createFolderMarker(normalizedPath, bucket);

        if (createTestFile) {
            createTestFile(normalizedPath, bucket);
        }
    }

    void createFolderMarker(String normalizedPath, String bucket) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(normalizedPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
    }

    void createTestFile(String normalizedPath, String bucket) throws Exception {
        String testFilePath = normalizedPath + "test.txt";
        String testContent = "This is a test file.";
        ByteArrayInputStream testStream = new ByteArrayInputStream(
                testContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(testFilePath)
                        .stream(testStream, testContent.length(), -1)
                        .contentType("text/plain")
                        .build()
        );
    }

    void deleteObjectQuietly(String objectName, String bucket) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception ignored) {
        }
    }

    Set<String> getAllowedExtensions() {
        if (configService == null) {
            return Collections.emptySet();
        }
        return configService.getAllowedExtensions();
    }

    Long getMaxFileSize() {
        if (configService == null) {
            return -1L;
        }
        return configService.getMaxFileSize();
    }

    String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    String getFileName(String objectName) {
        if (objectName == null) {
            return "";
        }
        String normalizedObjectName = objectName;
        if (normalizedObjectName.endsWith("/") && normalizedObjectName.length() > 1) {
            normalizedObjectName = normalizedObjectName.substring(0, normalizedObjectName.length() - 1);
        }
        int lastSlash = normalizedObjectName.lastIndexOf("/");
        if (lastSlash >= 0 && lastSlash < normalizedObjectName.length() - 1) {
            return normalizedObjectName.substring(lastSlash + 1);
        }
        return normalizedObjectName;
    }

    String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private boolean fileExists(String objectName, String bucket) {
        return objectExists(bucket, objectName);
    }

    boolean objectExists(String bucket, String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
