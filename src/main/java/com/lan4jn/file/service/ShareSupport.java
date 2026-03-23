package com.lan4jn.file.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ShareSupport {

    static final String BATCH_PREFIX = "batchjson:";
    static final String LEGACY_BATCH_PREFIX = "batch:";

    boolean isCollectionShare(ShareLink shareLink) {
        return Boolean.TRUE.equals(shareLink.getIsFolder());
    }

    boolean isBatchShare(ShareLink shareLink) {
        String objectName = shareLink.getObjectName();
        return objectName != null && (objectName.startsWith(BATCH_PREFIX) || objectName.startsWith(LEGACY_BATCH_PREFIX));
    }

    List<String> parseBatchRoots(String storedValue) {
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

    String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    String ensureTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    String extractFolderName(String objectName) {
        String normalized = objectName.endsWith("/") ? objectName.substring(0, objectName.length() - 1) : objectName;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    void validateObjectAccess(ShareLink shareLink, String objectName) {
        if (isBatchShare(shareLink)) {
            for (String root : parseBatchRoots(shareLink.getObjectName())) {
                if (Objects.equals(root, objectName)) {
                    return;
                }
                if (root.endsWith("/") && objectName.startsWith(root)) {
                    return;
                }
            }
            throw new RuntimeException("访问对象不在分享范围内");
        }

        if (!isCollectionShare(shareLink)) {
            if (!Objects.equals(shareLink.getObjectName(), objectName)) {
                throw new RuntimeException("访问对象不在分享范围内");
            }
            return;
        }

        String root = ensureTrailingSlash(shareLink.getObjectName());
        if (!objectName.startsWith(root)) {
            throw new RuntimeException("访问对象不在分享范围内");
        }
    }

    List<FileInfo> sortShareItems(List<FileInfo> items) {
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

    void putUniqueEntry(Map<String, FileInfo> entries, String entryName, FileInfo fileInfo) {
        String candidate = entryName;
        int index = 1;
        while (entries.containsKey(candidate)) {
            candidate = appendSuffix(entryName, index++);
        }
        entries.put(candidate, fileInfo);
    }

    String appendSuffix(String entryName, int index) {
        int slash = entryName.lastIndexOf('/');
        String prefix = slash >= 0 ? entryName.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return prefix + fileName + " (" + index + ")";
        }
        return prefix + fileName.substring(0, dot) + " (" + index + ")" + fileName.substring(dot);
    }

    String formatSize(long bytes) {
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

    Map<String, FileInfo> newEntryMap() {
        return new LinkedHashMap<>();
    }
}
