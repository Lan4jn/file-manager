package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.model.FileInfo;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FileQueryService {

    private static final Logger log = LoggerFactory.getLogger(FileQueryService.class);

    private final MinioClient minioClient;
    private final FileDomainSupport support;

    FileQueryService(MinioClient minioClient, FileDomainSupport support) {
        this.minioClient = minioClient;
        this.support = support;
    }

    List<FileInfo> listFiles(String folder, String bucket, String defaultBucket, String publicUrl,
                             String sortBy, String order) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedFolder = support.normalizeFolder(folder);
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(resolvedBucket)
                            .prefix(normalizedFolder)
                            .recursive(false)
                            .build()
            );

            List<FileInfo> fileList = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                if (objectName.equals(normalizedFolder)) {
                    continue;
                }

                boolean isFolder = item.isDir() || objectName.endsWith("/");
                if (!isFolder && item.size() == 0 && hasFolderWithSameName(fileList, support.getFileName(objectName))) {
                    continue;
                }

                fileList.add(support.buildFileInfo(item, resolvedBucket, objectName, isFolder, publicUrl));
            }

            sortFiles(fileList, sortBy, order);
            return fileList;
        } catch (Exception e) {
            log.error("Failed to list files", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
        }
    }

    PageResult<FileInfo> listFiles(String folder, String bucket, String defaultBucket, String publicUrl,
                                   String sortBy, String order, int page, int size) {
        int normalizedPage = page < 1 ? 1 : page;
        int normalizedSize = (size == 10 || size == 20 || size == 50 || size == 100) ? size : 10;
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedFolder = support.normalizeFolder(folder);

        if ((StrUtil.isBlank(sortBy) || "name".equalsIgnoreCase(sortBy)) && !"desc".equalsIgnoreCase(order)) {
            return listFilesFolderFirstPaged(normalizedFolder, resolvedBucket, publicUrl, normalizedPage, normalizedSize);
        }

        try {
            String startAfter = null;
            if (normalizedPage > 1) {
                startAfter = calculateStartAfterForPage(
                        normalizedFolder, resolvedBucket, publicUrl, sortBy, order, normalizedPage - 1, normalizedSize);
                if (startAfter == null) {
                    return new PageResult<>(new ArrayList<>(), 0, normalizedPage, normalizedSize);
                }
            }

            ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                    .bucket(resolvedBucket)
                    .prefix(normalizedFolder)
                    .recursive(false)
                    .maxKeys(normalizedSize + 1);
            if (startAfter != null) {
                argsBuilder.startAfter(startAfter);
            }

            Iterable<Result<Item>> results = minioClient.listObjects(argsBuilder.build());
            List<FileInfo> fileList = new ArrayList<>();
            String lastObjectName = null;
            boolean hasMore = false;
            int count = 0;

            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                if (objectName.equals(normalizedFolder)) {
                    continue;
                }

                boolean isFolder = item.isDir() || objectName.endsWith("/");
                if (!isFolder && item.size() == 0 && hasFolderWithSameName(fileList, support.getFileName(objectName))) {
                    continue;
                }

                count++;
                if (count > normalizedSize) {
                    hasMore = true;
                    continue;
                }

                lastObjectName = objectName;
                fileList.add(support.buildFileInfo(item, resolvedBucket, objectName, isFolder, publicUrl));
            }

            sortFiles(fileList, sortBy, order);
            long total = hasMore ? (long) normalizedPage * normalizedSize + 1 : fileList.size();
            return new PageResult<>(fileList, total, normalizedPage, normalizedSize, hasMore ? lastObjectName : null, hasMore);
        } catch (Exception e) {
            log.error("Failed to list files with pagination", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
        }
    }

    java.io.InputStream getFileStream(String objectName, String bucket, String defaultBucket) {
        return support.getFileStream(objectName, bucket, defaultBucket);
    }

    java.io.InputStream getFileStream(String objectName, String bucket, String defaultBucket, Long offset, Long length) {
        return support.getFileStream(objectName, bucket, defaultBucket, offset, length);
    }

    FileInfo getFileInfo(String objectName, String bucket, String defaultBucket, String publicUrl) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(resolvedBucket)
                            .object(objectName)
                            .build()
            );
            return support.buildFileInfo(stat, objectName, resolvedBucket, publicUrl);
        } catch (Exception e) {
            log.error("Failed to get file info: {}", objectName, e);
            throw new RuntimeException("获取文件信息失败: " + e.getMessage(), e);
        }
    }

    String getPreviewUrl(String objectName, String bucket, String defaultBucket, String publicUrl) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        return publicUrl + "/" + resolvedBucket + "/" + objectName;
    }

    List<FileInfo> listFilesRecursive(String folder, String bucket, String defaultBucket) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedFolder = support.normalizeFolder(folder);
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(resolvedBucket)
                            .prefix(normalizedFolder)
                            .recursive(true)
                            .build()
            );

            List<FileInfo> fileList = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() || item.objectName().endsWith("/")) {
                    continue;
                }
                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(item.objectName());
                String relativePath = item.objectName();
                if (StrUtil.isNotEmpty(normalizedFolder) && relativePath.startsWith(normalizedFolder)) {
                    relativePath = relativePath.substring(normalizedFolder.length());
                }
                fileInfo.setFileName(relativePath);
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(false);
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
                fileList.add(fileInfo);
            }
            return fileList;
        } catch (Exception e) {
            log.error("Failed to list files recursively", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
        }
    }

    private PageResult<FileInfo> listFilesFolderFirstPaged(String folder, String bucket, String publicUrl, int page, int size) {
        try {
            int startIndex = (page - 1) * size;
            int endIndex = startIndex + size;

            Iterable<Result<Item>> firstPass = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folder)
                            .recursive(false)
                            .build()
            );

            List<FileInfo> folders = new ArrayList<>();
            Set<String> folderNames = new HashSet<>();

            for (Result<Item> result : firstPass) {
                Item item = result.get();
                String objectName = item.objectName();
                if (objectName.equals(folder)) {
                    continue;
                }
                boolean isFolder = item.isDir() || objectName.endsWith("/");
                if (!isFolder) {
                    continue;
                }
                FileInfo fileInfo = support.buildFileInfo(item, bucket, objectName, true, publicUrl);
                folders.add(fileInfo);
                folderNames.add(fileInfo.getFileName());
            }

            folders.sort(Comparator.comparing(file -> file.getFileName() != null ? file.getFileName() : ""));

            List<FileInfo> pageItems = new ArrayList<>();
            if (startIndex < folders.size()) {
                int folderEnd = Math.min(endIndex, folders.size());
                pageItems.addAll(folders.subList(startIndex, folderEnd));
            }

            int fileStartIndex = Math.max(0, startIndex - folders.size());
            int fileSlots = size - pageItems.size();
            int fileCount = 0;
            boolean hasMoreFiles = false;

            if (fileSlots > 0) {
                Iterable<Result<Item>> secondPass = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(bucket)
                                .prefix(folder)
                                .recursive(false)
                                .build()
                );

                for (Result<Item> result : secondPass) {
                    Item item = result.get();
                    String objectName = item.objectName();
                    if (objectName.equals(folder)) {
                        continue;
                    }

                    boolean isFolder = item.isDir() || objectName.endsWith("/");
                    if (isFolder) {
                        continue;
                    }

                    String fileName = support.getFileName(objectName);
                    if (item.size() == 0 && folderNames.contains(fileName)) {
                        continue;
                    }

                    if (fileCount >= fileStartIndex && pageItems.size() < size) {
                        pageItems.add(support.buildFileInfo(item, bucket, objectName, false, publicUrl));
                    } else if (fileCount >= fileStartIndex + fileSlots) {
                        hasMoreFiles = true;
                        break;
                    }
                    fileCount++;
                }
            }

            long total = folders.size() + fileCount;
            boolean hasMore = endIndex < folders.size() || hasMoreFiles;
            if (hasMore && total <= endIndex) {
                total = endIndex + 1L;
            }
            String nextMarker = hasMore && !pageItems.isEmpty()
                    ? pageItems.get(pageItems.size() - 1).getObjectName()
                    : null;
            return new PageResult<>(pageItems, total, page, size, nextMarker, hasMore);
        } catch (Exception e) {
            log.error("Failed to list files with folder-first pagination", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
        }
    }

    private String calculateStartAfterForPage(String folder, String bucket, String publicUrl,
                                              String sortBy, String order, int targetPage, int size) {
        if (sortBy != null && !"".equals(sortBy) && !"name".equals(sortBy)) {
            return calculateStartAfterByFetchingAll(folder, bucket, publicUrl, sortBy, order, targetPage, size);
        }

        try {
            String startAfter = null;
            for (int i = 0; i < targetPage; i++) {
                ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(folder)
                        .recursive(false)
                        .maxKeys(size);
                if (startAfter != null) {
                    argsBuilder.startAfter(startAfter);
                }

                Iterable<Result<Item>> results = minioClient.listObjects(argsBuilder.build());
                String lastName = null;
                int count = 0;
                boolean hasMore = false;

                for (Result<Item> result : results) {
                    Item item = result.get();
                    String objectName = item.objectName();
                    if (objectName.equals(folder)) {
                        continue;
                    }
                    count++;
                    if (count > size) {
                        hasMore = true;
                        break;
                    }
                    lastName = objectName;
                }

                if (lastName == null) {
                    return null;
                }
                startAfter = lastName;
                if (!hasMore) {
                    return null;
                }
            }
            return startAfter;
        } catch (Exception e) {
            log.error("Failed to calculate startAfter for page {}", targetPage, e);
            return null;
        }
    }

    private String calculateStartAfterByFetchingAll(String folder, String bucket, String publicUrl,
                                                    String sortBy, String order, int targetPage, int size) {
        try {
            List<FileInfo> allFiles = new ArrayList<>();
            int maxTotal = 10000;
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folder)
                            .recursive(false)
                            .build()
            );

            for (Result<Item> result : results) {
                if (allFiles.size() >= maxTotal) {
                    break;
                }

                Item item = result.get();
                String objectName = item.objectName();
                if (objectName.equals(folder)) {
                    continue;
                }
                boolean isFolder = item.isDir() || objectName.endsWith("/");
                allFiles.add(support.buildFileInfo(item, bucket, objectName, isFolder, publicUrl));
            }

            sortFiles(allFiles, sortBy, order);
            int offset = (targetPage - 1) * size;
            if (offset >= allFiles.size()) {
                return null;
            }
            return allFiles.get(offset).getObjectName();
        } catch (Exception e) {
            log.error("Failed to calculate startAfter by fetching all", e);
            return null;
        }
    }

    private void sortFiles(List<FileInfo> fileList, String sortBy, String order) {
        fileList.sort((f1, f2) -> {
            boolean f1IsFolder = Boolean.TRUE.equals(f1.getIsFolder());
            boolean f2IsFolder = Boolean.TRUE.equals(f2.getIsFolder());

            if (f1IsFolder != f2IsFolder) {
                return f1IsFolder ? -1 : 1;
            }

            int result;
            switch (sortBy) {
                case "size":
                    result = Long.compare(f1.getSize() != null ? f1.getSize() : 0, f2.getSize() != null ? f2.getSize() : 0);
                    break;
                case "time":
                    result = valueOf(f1.getUploadTime()).compareTo(valueOf(f2.getUploadTime()));
                    break;
                default:
                    result = valueOf(f1.getFileName()).compareTo(valueOf(f2.getFileName()));
            }
            return "desc".equalsIgnoreCase(order) ? -result : result;
        });
    }

    private boolean hasFolderWithSameName(List<FileInfo> fileList, String fileName) {
        for (FileInfo fi : fileList) {
            if (Boolean.TRUE.equals(fi.getIsFolder()) && valueOf(fi.getFileName()).equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String valueOf(String value) {
        return value != null ? value : "";
    }

    private LocalDateTime valueOf(LocalDateTime value) {
        return value != null ? value : LocalDateTime.MIN;
    }
}
