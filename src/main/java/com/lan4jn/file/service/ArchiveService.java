package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.model.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    private final FileDomainSupport support;
    private final FileQueryService fileQueryService;
    private final ConfigService configService;

    ArchiveService(FileDomainSupport support, FileQueryService fileQueryService, ConfigService configService) {
        this.support = support;
        this.fileQueryService = fileQueryService;
        this.configService = configService;
    }

    void downloadFolderAsZip(String folder, String bucket, String defaultBucket, OutputStream outputStream) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedFolder = support.normalizeFolder(folder);
        try {
            streamFilesAsZip(resolveFolderEntries(normalizedFolder, resolvedBucket), resolvedBucket, defaultBucket, outputStream);
            log.info("Folder downloaded as ZIP: {}", normalizedFolder);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download folder as ZIP: {}", normalizedFolder, e);
            throw new RuntimeException("目录下载失败: " + e.getMessage(), e);
        }
    }

    void downloadBatchAsZip(List<String> objectNames, String bucket, String defaultBucket, OutputStream outputStream) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        try {
            streamFilesAsZip(resolveBatchEntries(objectNames, resolvedBucket), resolvedBucket, defaultBucket, outputStream);
            log.info("Batch files downloaded as ZIP: {} items", objectNames.size());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download batch as ZIP", e);
            throw new RuntimeException("批量下载失败: " + e.getMessage(), e);
        }
    }

    List<FileInfo> resolveFolderEntries(String folder, String bucket) {
        List<FileInfo> files = fileQueryService.listFilesRecursive(folder, bucket, bucket);
        assertArchiveWithinLimit(files, "目录压缩后大小超过限制");
        return files;
    }

    List<FileInfo> resolveBatchEntries(List<String> objectNames, String bucket) {
        java.util.ArrayList<FileInfo> files = new java.util.ArrayList<>();
        for (String objectName : objectNames) {
            if (objectName.endsWith("/")) {
                files.addAll(fileQueryService.listFilesRecursive(objectName, bucket, bucket));
            } else {
                files.add(fileQueryService.getFileInfo(objectName, bucket, bucket, ""));
            }
        }
        assertArchiveWithinLimit(files, "批量下载总大小超过限制");
        return files;
    }

    void streamFilesAsZip(List<FileInfo> files, String bucket, String defaultBucket, OutputStream outputStream) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            byte[] buffer = new byte[8192];
            long totalWritten = 0L;
            for (FileInfo file : files) {
                totalWritten = addFileToZip(zos, file, bucket, defaultBucket, buffer, totalWritten);
            }
            zos.finish();
        }
    }

    private void addFileToZip(ZipOutputStream zos, FileInfo fileInfo, String bucket, String defaultBucket, byte[] buffer)
            throws Exception {
        addFileToZip(zos, fileInfo, bucket, defaultBucket, buffer, 0L);
    }

    private long addFileToZip(ZipOutputStream zos, FileInfo fileInfo, String bucket, String defaultBucket, byte[] buffer,
                              long totalWritten) throws Exception {
        try (InputStream is = support.getFileStream(fileInfo.getObjectName(), bucket, defaultBucket)) {
            String entryName = fileInfo.getObjectName();
            if (StrUtil.isBlank(entryName) || entryName.endsWith("/")) {
                return totalWritten;
            }
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);

            int len;
            while ((len = is.read(buffer)) > 0) {
                totalWritten += len;
                long maxArchiveSize = getMaxArchiveSize();
                if (maxArchiveSize > 0 && totalWritten > maxArchiveSize) {
                    throw new RuntimeException("批量下载压缩后大小超过限制(" + formatSize(maxArchiveSize) + ")，无法下载");
                }
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
            return totalWritten;
        }
    }

    private void assertArchiveWithinLimit(List<FileInfo> files, String message) {
        long maxArchiveSize = getMaxArchiveSize();
        if (maxArchiveSize <= 0) {
            return;
        }
        long totalSize = 0L;
        for (FileInfo file : files) {
            totalSize += file.getSize() != null ? file.getSize() : 0L;
        }
        if (totalSize > maxArchiveSize) {
            throw new RuntimeException(message + "(" + formatSize(maxArchiveSize) + ")");
        }
    }

    private long getMaxArchiveSize() {
        Long maxSize = configService.getBatchDownloadMaxSize();
        return maxSize == null ? -1L : maxSize;
    }

    private String formatSize(long size) {
        if (size < 0) {
            return "无限制";
        }
        double value = size;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(java.util.Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }
}
