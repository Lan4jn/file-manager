package com.lan4jn.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final String BATCH_PREFIX = "batchjson:";
    private static final String LEGACY_BATCH_PREFIX = "batch:";

    private final FileService fileService;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareFileRecordRepository shareFileRecordRepository;
    private final ConfigService configService;

    @Value("${share.default-expiration: 24}")
    private Integer defaultExpiration;

    public ShareService(FileService fileService, ShareLinkRepository shareLinkRepository,
                        ShareFileRecordRepository shareFileRecordRepository, ConfigService configService) {
        this.fileService = fileService;
        this.shareLinkRepository = shareLinkRepository;
        this.shareFileRecordRepository = shareFileRecordRepository;
        this.configService = configService;
    }

    public ShareLink createShareLink(CreateShareRequest request, String createBy) {
        String shareCode = generateShareCode();
        Integer expirationHours = request.getExpirationHours();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        String bucket = request.getBucket();
        String objectName = request.getObjectName();
        boolean isFolder = objectName.endsWith("/");

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(objectName);
        shareLink.setBucket(bucket);
        shareLink.setFileName(isFolder ? extractFolderName(objectName) : fileService.getFileInfo(objectName, bucket).getFileName());
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(request.getPassword()));
        shareLink.setPassword(request.getPassword());
        shareLink.setIsFolder(isFolder);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        log.info("Share link created: {}", shareCode);
        return saved;
    }

    public ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                          Integer expirationHours, String password) {
        if (objectNames == null || objectNames.isEmpty()) {
            throw new RuntimeException("璇烽€夋嫨瑕佸垎浜殑鏂囦欢");
        }

        String shareCode = generateShareCode();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(BATCH_PREFIX + JSONUtil.toJsonStr(objectNames));
        shareLink.setBucket(bucket);
        shareLink.setFileName("鎵归噺鍒嗕韩_" + objectNames.size() + "涓」鐩?");
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(password));
        shareLink.setPassword(password);
        shareLink.setIsFolder(true);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        log.info("Batch share link created: {}", shareCode);
        return saved;
    }

    public ShareAccessInfo getShareAccessInfo(String shareCode) {
        ShareLink shareLink = findValidShare(shareCode);
        ShareAccessInfo info = new ShareAccessInfo();
        info.setShareCode(shareLink.getShareCode());
        info.setFileName(shareLink.getFileName());
        info.setHasPassword(Boolean.TRUE.equals(shareLink.getHasPassword()));
        info.setIsFolder(Boolean.TRUE.equals(shareLink.getIsFolder()));
        info.setExpireTime(shareLink.getExpireTime());
        return info;
    }

    public ShareLink getShareInfo(String shareCode, String password) {
        return resolveShare(shareCode, password, true);
    }

    public FileInfo getFileByShareCode(String shareCode, String password) {
        ShareLink shareLink = resolveShare(shareCode, password, false);
        if (isCollectionShare(shareLink)) {
            FileInfo info = new FileInfo();
            info.setObjectName(shareLink.getObjectName());
            info.setFileName(shareLink.getFileName());
            info.setIsFolder(true);
            info.setSize(0L);
            return info;
        }
        return fileService.getFileInfo(shareLink.getObjectName(), shareLink.getBucket());
    }

    public List<FileInfo> listSharedItems(String shareCode, String password, String path) {
        ShareLink shareLink = resolveShare(shareCode, password, false);
        if (!isCollectionShare(shareLink)) {
            return new ArrayList<>();
        }

        String currentPath = normalizePath(path);
        if (isBatchShare(shareLink)) {
            return listBatchShareItems(shareLink, currentPath);
        }
        return listFolderShareItems(shareLink, currentPath);
    }

    public FileInfo getSharedItemInfo(String shareCode, String password, String objectName) {
        ShareLink shareLink = resolveShare(shareCode, password, false);
        if (StrUtil.isBlank(objectName)) {
            throw new RuntimeException("缂哄皯鏂囦欢璺緞");
        }
        validateObjectAccess(shareLink, objectName);
        if (objectName.endsWith("/")) {
            FileInfo folderInfo = new FileInfo();
            folderInfo.setObjectName(objectName);
            folderInfo.setFileName(extractFolderName(objectName));
            folderInfo.setIsFolder(true);
            folderInfo.setSize(0L);
            return folderInfo;
        }
        return fileService.getFileInfo(objectName, shareLink.getBucket());
    }

    public void downloadShareAsZip(String shareCode, String password, OutputStream outputStream) {
        ShareLink shareLink = resolveShare(shareCode, password, false);
        if (!isCollectionShare(shareLink)) {
            throw new RuntimeException("褰撳墠鍒嗕韩涓嶆槸鐩綍鎴栨壒閲忓垎浜?");
        }

        try {
            Map<String, FileInfo> entries = collectShareDownloadEntries(shareLink);
            long totalSize = 0L;
            for (FileInfo fileInfo : entries.values()) {
                totalSize += fileInfo.getSize() != null ? fileInfo.getSize() : 0L;
            }

            long maxSize = configService.getBatchDownloadMaxSize();
            if (maxSize > 0 && totalSize > maxSize) {
                throw new RuntimeException("鍒嗕韩鍐呭鍘嬬缉鍚庡ぇ灏忚秴杩囬檺鍒?" + formatSize(maxSize) + ")锛屾棤娉曚笅杞?");
            }

            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];
                for (Map.Entry<String, FileInfo> entry : entries.entrySet()) {
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
            log.error("Failed to download share as zip: {}", shareCode, e);
            throw new RuntimeException("鎵撳寘鍒嗕韩鍐呭澶辫触: " + e.getMessage());
        }
    }

    public String getShareArchiveName(String shareCode, String password) {
        ShareLink shareLink = resolveShare(shareCode, password, false);
        String fileName = StrUtil.blankToDefault(shareLink.getFileName(), "share");
        return fileName + ".zip";
    }

    public boolean validateShareCode(String shareCode) {
        Optional<ShareLink> shareLink = shareLinkRepository.findByShareCode(shareCode);
        return shareLink.isPresent() && !LocalDateTime.now().isAfter(shareLink.get().getExpireTime());
    }

    public void deleteShareLink(String shareCode, String createBy) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("鍒嗕韩閾炬帴涓嶅瓨鍦?"));
        if (!Objects.equals(shareLink.getCreateBy(), createBy)) {
            throw new RuntimeException("鏃犳潈鍒犻櫎璇ュ垎浜?");
        }
        shareFileRecordRepository.deleteByShareCode(shareCode);
        shareLinkRepository.delete(shareLink);
        log.info("Share link deleted: {}", shareCode);
    }

    public List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkRepository.findByCreateByOrderByCreateTimeDesc(createBy);
    }

    public void cleanupExpiredShares() {
        log.info("Share cleanup skipped: modern share mode no longer stores ZIP archives");
    }

    private List<FileInfo> listFolderShareItems(ShareLink shareLink, String path) {
        String root = ensureTrailingSlash(shareLink.getObjectName());
        String targetPath = StrUtil.isBlank(path) ? root : ensureTrailingSlash(path);
        if (!targetPath.startsWith(root)) {
            throw new RuntimeException("璁块棶璺緞涓嶅悎娉?");
        }
        return sortShareItems(fileService.listFiles(targetPath, shareLink.getBucket(), "name", "asc"));
    }

    private List<FileInfo> listBatchShareItems(ShareLink shareLink, String path) {
        List<String> roots = parseBatchRoots(shareLink.getObjectName());
        if (StrUtil.isBlank(path)) {
            List<FileInfo> rootItems = new ArrayList<>();
            for (String root : roots) {
                rootItems.add(buildRootEntry(shareLink.getBucket(), root));
            }
            return sortShareItems(rootItems);
        }

        validateObjectAccess(shareLink, path);
        if (!path.endsWith("/")) {
            throw new RuntimeException("褰撳墠璺緞涓嶆槸鐩綍");
        }
        return sortShareItems(fileService.listFiles(path, shareLink.getBucket(), "name", "asc"));
    }

    private FileInfo buildRootEntry(String bucket, String objectName) {
        if (objectName.endsWith("/")) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setObjectName(objectName);
            fileInfo.setFileName(extractFolderName(objectName));
            fileInfo.setIsFolder(true);
            fileInfo.setSize(0L);
            return fileInfo;
        }
        return fileService.getFileInfo(objectName, bucket);
    }

    private void validateObjectAccess(ShareLink shareLink, String objectName) {
        if (isBatchShare(shareLink)) {
            for (String root : parseBatchRoots(shareLink.getObjectName())) {
                if (Objects.equals(root, objectName)) {
                    return;
                }
                if (root.endsWith("/") && objectName.startsWith(root)) {
                    return;
                }
            }
            throw new RuntimeException("璁块棶璺緞涓嶅湪鍒嗕韩鑼冨洿鍐?");
        }

        if (!isCollectionShare(shareLink)) {
            if (!Objects.equals(shareLink.getObjectName(), objectName)) {
                throw new RuntimeException("璁块棶璺緞涓嶅湪鍒嗕韩鑼冨洿鍐?");
            }
            return;
        }

        String root = ensureTrailingSlash(shareLink.getObjectName());
        String normalized = objectName.endsWith("/") ? objectName : objectName;
        if (!normalized.startsWith(root)) {
            throw new RuntimeException("璁块棶璺緞涓嶅湪鍒嗕韩鑼冨洿鍐?");
        }
    }

    private boolean isCollectionShare(ShareLink shareLink) {
        return Boolean.TRUE.equals(shareLink.getIsFolder());
    }

    private Map<String, FileInfo> collectShareDownloadEntries(ShareLink shareLink) {
        Map<String, FileInfo> entries = new LinkedHashMap<>();
        if (isBatchShare(shareLink)) {
            for (String root : parseBatchRoots(shareLink.getObjectName())) {
                if (root.endsWith("/")) {
                    List<FileInfo> files = fileService.listFilesRecursive(root, shareLink.getBucket());
                    String rootName = extractFolderName(root);
                    for (FileInfo fileInfo : files) {
                        String entryName = rootName + "/" + fileInfo.getFileName();
                        putUniqueEntry(entries, entryName, fileInfo);
                    }
                } else {
                    FileInfo fileInfo = fileService.getFileInfo(root, shareLink.getBucket());
                    putUniqueEntry(entries, fileInfo.getFileName(), fileInfo);
                }
            }
            return entries;
        }

        String root = ensureTrailingSlash(shareLink.getObjectName());
        List<FileInfo> files = fileService.listFilesRecursive(root, shareLink.getBucket());
        for (FileInfo fileInfo : files) {
            putUniqueEntry(entries, fileInfo.getFileName(), fileInfo);
        }
        return entries;
    }

    private void putUniqueEntry(Map<String, FileInfo> entries, String entryName, FileInfo fileInfo) {
        String candidate = entryName;
        int index = 1;
        while (entries.containsKey(candidate)) {
            candidate = appendSuffix(entryName, index++);
        }
        entries.put(candidate, fileInfo);
    }

    private String appendSuffix(String entryName, int index) {
        int slash = entryName.lastIndexOf('/');
        String prefix = slash >= 0 ? entryName.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return prefix + fileName + " (" + index + ")";
        }
        return prefix + fileName.substring(0, dot) + " (" + index + ")" + fileName.substring(dot);
    }

    private boolean isBatchShare(ShareLink shareLink) {
        String objectName = shareLink.getObjectName();
        return objectName != null && (objectName.startsWith(BATCH_PREFIX) || objectName.startsWith(LEGACY_BATCH_PREFIX));
    }

    private List<String> parseBatchRoots(String storedValue) {
        if (storedValue == null) {
            return new ArrayList<>();
        }
        if (storedValue.startsWith(BATCH_PREFIX)) {
            return JSONUtil.toList(storedValue.substring(BATCH_PREFIX.length()), String.class);
        }
        if (storedValue.startsWith(LEGACY_BATCH_PREFIX)) {
            String raw = storedValue.substring(LEGACY_BATCH_PREFIX.length());
            List<String> result = new ArrayList<>();
            if (StrUtil.isNotBlank(raw)) {
                for (String item : raw.split(",")) {
                    if (StrUtil.isNotBlank(item)) {
                        result.add(item);
                    }
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private ShareLink resolveShare(String shareCode, String password, boolean incrementView) {
        ShareLink shareLink = findValidShare(shareCode);
        if (Boolean.TRUE.equals(shareLink.getHasPassword())) {
            if (StrUtil.isBlank(password)) {
                throw new RuntimeException("璇疯緭鍏ユ彁鍙栫爜");
            }
            if (!Objects.equals(password, shareLink.getPassword())) {
                throw new RuntimeException("鎻愬彇鐮侀敊璇?");
            }
        }

        if (incrementView) {
            shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1);
            shareLinkRepository.save(shareLink);
        }
        return shareLink;
    }

    private ShareLink findValidShare(String shareCode) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("鍒嗕韩閾炬帴涓嶅瓨鍦?"));
        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("鍒嗕韩閾炬帴宸茶繃鏈?");
        }
        return shareLink;
    }

    private List<FileInfo> sortShareItems(List<FileInfo> items) {
        items.sort(new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo a, FileInfo b) {
                boolean aFolder = Boolean.TRUE.equals(a.getIsFolder());
                boolean bFolder = Boolean.TRUE.equals(b.getIsFolder());
                if (aFolder != bFolder) {
                    return aFolder ? -1 : 1;
                }
                String aName = a.getFileName() == null ? "" : a.getFileName();
                String bName = b.getFileName() == null ? "" : b.getFileName();
                return aName.compareToIgnoreCase(bName);
            }
        });
        return items;
    }

    private String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private String extractFolderName(String objectName) {
        String normalized = objectName.endsWith("/") ? objectName.substring(0, objectName.length() - 1) : objectName;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private String generateShareCode() {
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

