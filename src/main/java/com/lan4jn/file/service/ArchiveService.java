package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.model.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private static final long MAX_ARCHIVE_SIZE = 2147483648L;

    private final FileDomainSupport support;
    private final FileQueryService fileQueryService;

    ArchiveService(FileDomainSupport support, FileQueryService fileQueryService) {
        this.support = support;
        this.fileQueryService = fileQueryService;
    }

    void downloadFolderAsZip(String folder, String bucket, String defaultBucket, OutputStream outputStream) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        String normalizedFolder = support.normalizeFolder(folder);
        try {
            List<FileInfo> files = fileQueryService.listFilesRecursive(normalizedFolder, resolvedBucket, resolvedBucket);
            long totalSize = 0;
            for (FileInfo file : files) {
                totalSize += file.getSize() != null ? file.getSize() : 0;
            }
            if (totalSize > MAX_ARCHIVE_SIZE) {
                throw new RuntimeException("目录压缩后大小超过2GB，无法下载");
            }

            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];
                for (FileInfo file : files) {
                    addFileToZip(zos, file, resolvedBucket, defaultBucket, buffer);
                }
                zos.finish();
            }
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
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];
                for (String objectName : objectNames) {
                    if (objectName.endsWith("/")) {
                        try {
                            List<FileInfo> folderFiles = fileQueryService.listFilesRecursive(objectName, resolvedBucket, resolvedBucket);
                            for (FileInfo subFile : folderFiles) {
                                addFileToZip(zos, subFile, resolvedBucket, defaultBucket, buffer);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to list folder {}: {}", objectName, e.getMessage());
                        }
                    } else {
                        try {
                            FileInfo fileInfo = fileQueryService.getFileInfo(objectName, resolvedBucket, resolvedBucket, "");
                            addFileToZip(zos, fileInfo, resolvedBucket, defaultBucket, buffer);
                        } catch (Exception e) {
                            log.warn("Failed to get file {}: {}", objectName, e.getMessage());
                        }
                    }
                }
                zos.finish();
            }
            log.info("Batch files downloaded as ZIP: {} items", objectNames.size());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download batch as ZIP", e);
            throw new RuntimeException("批量下载失败: " + e.getMessage(), e);
        }
    }

    private void addFileToZip(ZipOutputStream zos, FileInfo fileInfo, String bucket, String defaultBucket, byte[] buffer)
            throws Exception {
        try (InputStream is = support.getFileStream(fileInfo.getObjectName(), bucket, defaultBucket)) {
            String entryName = fileInfo.getObjectName();
            if (StrUtil.isBlank(entryName)) {
                return;
            }
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);

            long bytesWritten = 0;
            int len;
            while ((len = is.read(buffer)) > 0) {
                bytesWritten += len;
                if (bytesWritten > MAX_ARCHIVE_SIZE) {
                    throw new RuntimeException("批量下载压缩后大小超过2GB，无法下载");
                }
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
        }
    }
}
