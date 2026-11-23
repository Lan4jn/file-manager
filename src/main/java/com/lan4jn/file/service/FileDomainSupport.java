package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.FileInfo;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class FileDomainSupport {

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
        fileInfo.setUploadTime(resolveUploadTime(item));
        fileInfo.setUrl(buildPublicObjectUrl(publicUrl, bucket, objectName));
        return fileInfo;
    }

    FileInfo buildFileInfo(StatObjectResponse stat, String objectName, String bucket, String publicUrl) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName(objectName);
        fileInfo.setFileName(getFileName(objectName));
        fileInfo.setSize(stat.size());
        fileInfo.setContentType(stat.contentType());
        fileInfo.setEtag(stat.etag());
        fileInfo.setUploadTime(resolveUploadTime(stat));
        fileInfo.setUrl(buildPublicObjectUrl(publicUrl, bucket, objectName));
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
        response.setUrl(buildPublicObjectUrl(publicUrl, bucket, objectName));
        response.setEtag(stat == null ? null : stat.etag());
        return response;
    }

    LocalDateTime resolveUploadTime(Item item) {
        if (item == null) {
            return null;
        }
        try {
            ZonedDateTime lastModified = item.lastModified();
            if (lastModified == null) {
                return null;
            }
            return LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    LocalDateTime resolveUploadTime(StatObjectResponse stat) {
        if (stat == null) {
            return null;
        }
        try {
            ZonedDateTime lastModified = stat.lastModified();
            if (lastModified == null) {
                return null;
            }
            return LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    String buildPublicObjectUrl(String publicUrl, String bucket, String objectName) {
        if (StrUtil.isBlank(publicUrl)) {
            return "";
        }
        String normalizedBase = publicUrl.endsWith("/")
                ? publicUrl.substring(0, publicUrl.length() - 1)
                : publicUrl;
        String encodedBucket = UriUtils.encodePathSegment(bucket, StandardCharsets.UTF_8);
        String encodedObjectName = Arrays.stream((objectName == null ? "" : objectName).split("/", -1))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
        return normalizedBase + "/" + encodedBucket + "/" + encodedObjectName;
    }

    String getPresignedDownloadUrl(String bucket, String objectName, String fileName, Integer expirySeconds) {
        try {
            Map<String, String> extraQueryParams = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(fileName)) {
                extraQueryParams.put("response-content-disposition",
                        "attachment; filename*=UTF-8''" + UriUtils.encode(fileName, StandardCharsets.UTF_8));
            }
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .extraQueryParams(extraQueryParams)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("生成下载直链失败: " + e.getMessage(), e);
        }
    }

    String getPresignedUploadUrl(String bucket, String objectName, Integer expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("生成上传直传地址失败: " + e.getMessage(), e);
        }
    }

    PreparedUpload prepareUpload(String originalFilename, Long size, String folder, String bucket, String defaultBucket) {
        String normalizedFileName = normalizeFileName(originalFilename);
        validateUploadRequest(normalizedFileName, size);
        String resolvedBucket = resolveBucket(bucket, defaultBucket);
        String objectName = generateObjectName(folder, normalizedFileName, resolvedBucket);
        return new PreparedUpload(resolvedBucket, objectName, normalizedFileName, size == null ? 0L : Math.max(size, 0L));
    }

    InputStream getFileStream(String objectName, String bucket, String defaultBucket) {
        return getFileStream(objectName, bucket, defaultBucket, null, null);
    }

    InputStream getFileStream(String objectName, String bucket, String defaultBucket, Long offset, Long length) {
        String resolvedBucket = resolveBucket(bucket, defaultBucket);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
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
                lastError = new RuntimeException("获取文件失败: " + e.getMessage(), e);
                if (attempt >= 3 || !isTransientGetObjectFailure(e)) {
                    throw lastError;
                }
            }
        }
        throw lastError == null ? new RuntimeException("获取文件失败") : lastError;
    }

    private boolean isTransientGetObjectFailure(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("timeout exceeded")
                || message.contains("timed out")
                || message.contains("reduce your request rate")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("broken pipe");
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

    void validateUploadRequest(String filename, Long size) {
        String normalizedFileName = normalizeFileName(filename);
        String extension = getFileExtension(normalizedFileName).toLowerCase();
        Set<String> allowedExtensions = getAllowedExtensions();
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
            throw new RuntimeException("不允许上传此类型文件: " + extension);
        }
        long actualSize = size == null ? 0L : Math.max(size, 0L);
        Long maxSize = getMaxFileSize();
        if (maxSize > 0 && actualSize > maxSize) {
            throw new RuntimeException("文件大小超出限制: 最大" + formatSize(maxSize));
        }
    }

    String normalizeFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("fileName不能为空");
        }
        return filename.trim();
    }

    String normalizeEntryName(String name) {
        String normalized = normalizeFileName(name);
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("名称不合法，不能包含路径分隔符");
        }
        return normalized;
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

    public static class PreparedUpload {
        private final String bucket;
        private final String objectName;
        private final String fileName;
        private final long size;

        public PreparedUpload(String bucket, String objectName, String fileName, long size) {
            this.bucket = bucket;
            this.objectName = objectName;
            this.fileName = fileName;
            this.size = size;
        }

        public String getBucket() {
            return bucket;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getFileName() {
            return fileName;
        }

        public long getSize() {
            return size;
        }
    }
}
