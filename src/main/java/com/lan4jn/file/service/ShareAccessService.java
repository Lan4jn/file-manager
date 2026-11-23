package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ShareAccessService {

    private static final Logger log = LoggerFactory.getLogger(ShareAccessService.class);

    private final FileService fileService;
    private final ConfigService configService;
    private final ShareLinkService shareLinkService;
    private final ShareSupport shareSupport;

    ShareAccessService(FileService fileService,
                       ConfigService configService,
                       ShareLinkService shareLinkService,
                       ShareSupport shareSupport) {
        this.fileService = fileService;
        this.configService = configService;
        this.shareLinkService = shareLinkService;
        this.shareSupport = shareSupport;
    }

    FileInfo getFileByShareCode(String shareCode, String password) {
        ShareLink shareLink = shareLinkService.resolveShare(shareCode, password, false);
        if (shareSupport.isCollectionShare(shareLink)) {
            FileInfo info = new FileInfo();
            info.setObjectName(shareLink.getObjectName());
            info.setFileName(shareLink.getFileName());
            info.setIsFolder(true);
            info.setSize(0L);
            return info;
        }
        return loadSharedFileInfo(shareLink.getObjectName(), shareLink.getBucket());
    }

    List<FileInfo> listSharedItems(String shareCode, String password, String path) {
        ShareLink shareLink = shareLinkService.resolveShare(shareCode, password, false);
        if (!shareSupport.isCollectionShare(shareLink)) {
            return new ArrayList<>();
        }

        String currentPath = shareSupport.normalizePath(path);
        if (shareSupport.isBatchShare(shareLink)) {
            return listBatchShareItems(shareLink, currentPath);
        }
        return listFolderShareItems(shareLink, currentPath);
    }

    FileInfo getSharedItemInfo(String shareCode, String password, String objectName) {
        ShareLink shareLink = shareLinkService.resolveShare(shareCode, password, false);
        if (StrUtil.isBlank(objectName)) {
            throw new RuntimeException("对象名称不能为空");
        }
        shareSupport.validateObjectAccess(shareLink, objectName);
        if (objectName.endsWith("/")) {
            FileInfo folderInfo = new FileInfo();
            folderInfo.setObjectName(objectName);
            folderInfo.setFileName(shareSupport.extractFolderName(objectName));
            folderInfo.setIsFolder(true);
            folderInfo.setSize(0L);
            return folderInfo;
        }
        return loadSharedFileInfo(objectName, shareLink.getBucket());
    }

    void downloadShareAsZip(String shareCode, String password, OutputStream outputStream) {
        ShareArchivePlan plan = prepareShareArchive(shareCode, password);
        downloadShareAsZip(plan, outputStream);
    }

    ShareArchivePlan prepareShareArchive(String shareCode, String password) {
        ShareLink shareLink = shareLinkService.resolveShare(shareCode, password, false);
        if (!shareSupport.isCollectionShare(shareLink)) {
            throw new RuntimeException("当前分享不是目录或集合分享");
        }
        try {
            Map<String, FileInfo> entries = collectShareDownloadEntries(shareLink);
            long totalSize = 0L;
            for (FileInfo fileInfo : entries.values()) {
                totalSize += fileInfo.getSize() != null ? fileInfo.getSize() : 0L;
            }

            long maxSize = configService.getBatchDownloadMaxSize();
            if (maxSize > 0 && totalSize > maxSize) {
                throw new RuntimeException("分享打包大小超过限制(" + shareSupport.formatSize(maxSize) + ")");
            }
            return new ShareArchivePlan(shareLink, new LinkedHashMap<>(entries));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to prepare share archive: {}", shareCode, e);
            throw new RuntimeException("分享下载失败: " + e.getMessage(), e);
        }
    }

    void downloadShareAsZip(ShareArchivePlan plan, OutputStream outputStream) {
        ShareLink shareLink = plan.getShareLink();
        try {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                byte[] buffer = new byte[8192];
                for (Map.Entry<String, FileInfo> entry : plan.getEntries().entrySet()) {
                    if (entry.getValue() == null || StrUtil.isBlank(entry.getValue().getObjectName())
                            || entry.getValue().getObjectName().endsWith("/")) {
                        continue;
                    }
                    try (InputStream inputStream = fileService.getFileStream(entry.getValue().getObjectName(), shareLink.getBucket())) {
                        zos.putNextEntry(new ZipEntry(entry.getKey()));
                        int len;
                        while ((len = inputStream.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                }
                zos.finish();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download share as zip: {}", shareLink.getShareCode(), e);
            throw new RuntimeException("分享下载失败: " + e.getMessage(), e);
        }
    }

    String getShareArchiveName(String shareCode, String password) {
        ShareLink shareLink = shareLinkService.resolveShare(shareCode, password, false);
        String fileName = StrUtil.blankToDefault(shareLink.getFileName(), "share");
        return fileName + ".zip";
    }

    private List<FileInfo> listFolderShareItems(ShareLink shareLink, String path) {
        String root = shareSupport.ensureTrailingSlash(shareLink.getObjectName());
        String targetPath = StrUtil.isBlank(path) ? root : shareSupport.ensureTrailingSlash(path);
        if (!targetPath.startsWith(root)) {
            throw new RuntimeException("路径不在分享范围内");
        }
        return shareSupport.sortShareItems(fileService.listFiles(targetPath, shareLink.getBucket(), "name", "asc"));
    }

    private List<FileInfo> listBatchShareItems(ShareLink shareLink, String path) {
        List<String> roots = shareSupport.parseBatchRoots(shareLink.getObjectName());
        if (StrUtil.isBlank(path)) {
            List<FileInfo> rootItems = new ArrayList<>();
            for (String root : roots) {
                rootItems.add(buildRootEntry(shareLink.getBucket(), root));
            }
            return shareSupport.sortShareItems(rootItems);
        }

        shareSupport.validateObjectAccess(shareLink, path);
        if (!path.endsWith("/")) {
            throw new RuntimeException("当前路径不是目录");
        }
        return shareSupport.sortShareItems(fileService.listFiles(path, shareLink.getBucket(), "name", "asc"));
    }

    private FileInfo buildRootEntry(String bucket, String objectName) {
        if (objectName.endsWith("/")) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setObjectName(objectName);
            fileInfo.setFileName(shareSupport.extractFolderName(objectName));
            fileInfo.setIsFolder(true);
            fileInfo.setSize(0L);
            return fileInfo;
        }
        return loadSharedFileInfo(objectName, bucket);
    }

    private Map<String, FileInfo> collectShareDownloadEntries(ShareLink shareLink) {
        Map<String, FileInfo> entries = shareSupport.newEntryMap();
        if (shareSupport.isBatchShare(shareLink)) {
            for (String root : shareSupport.parseBatchRoots(shareLink.getObjectName())) {
                if (root.endsWith("/")) {
                    List<FileInfo> files = fileService.listFilesRecursive(root, shareLink.getBucket());
                    String rootName = shareSupport.extractFolderName(root);
                    for (FileInfo fileInfo : files) {
                        String entryName = rootName + "/" + fileInfo.getFileName();
                        shareSupport.putUniqueEntry(entries, entryName, fileInfo);
                    }
                } else {
                    FileInfo fileInfo = loadSharedFileInfo(root, shareLink.getBucket());
                    shareSupport.putUniqueEntry(entries, fileInfo.getFileName(), fileInfo);
                }
            }
            return entries;
        }

        String root = shareSupport.ensureTrailingSlash(shareLink.getObjectName());
        List<FileInfo> files = fileService.listFilesRecursive(root, shareLink.getBucket());
        for (FileInfo fileInfo : files) {
            shareSupport.putUniqueEntry(entries, fileInfo.getFileName(), fileInfo);
        }
        return entries;
    }

    private FileInfo loadSharedFileInfo(String objectName, String bucket) {
        try {
            return fileService.getFileInfo(objectName, bucket);
        } catch (RuntimeException e) {
            if (isMissingSharedContent(e)) {
                throw new RuntimeException("分享内容已被删除或移动", e);
            }
            throw new RuntimeException("分享内容加载失败，请稍后重试", e);
        }
    }

    private boolean isMissingSharedContent(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("object does not exist")
                || normalized.contains("not found")
                || normalized.contains("no such key")
                || normalized.contains("nosuchkey")
                || normalized.contains("404");
    }
}
