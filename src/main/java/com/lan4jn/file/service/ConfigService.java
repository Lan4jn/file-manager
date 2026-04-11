package com.lan4jn.file.service;

import com.lan4jn.file.entity.SysConfig;
import com.lan4jn.file.repository.ConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConfigService {

    public static final String KEY_ALLOWED_EXTENSIONS = "allowed_extensions";
    public static final String KEY_MAX_FILE_SIZE = "max_file_size";
    public static final String KEY_FRONTEND_DOWNLOAD_THRESHOLD = "frontend_download_threshold";
    public static final String KEY_BATCH_SHARE_MAX_SIZE = "batch_share_max_size";
    public static final String KEY_BATCH_DOWNLOAD_MAX_SIZE = "batch_download_max_size";
    public static final String KEY_SHARE_EXPIRE_HOURS = "share_expire_hours";
    public static final String KEY_UPLOAD_TRANSPORT_MODE = "upload_transport_mode";
    public static final String KEY_DOWNLOAD_TRANSPORT_MODE = "download_transport_mode";
    public static final String KEY_UPLOAD_PROXY_THRESHOLD = "upload_proxy_threshold_bytes";
    public static final String KEY_DOWNLOAD_PROXY_THRESHOLD = "download_proxy_threshold_bytes";
    public static final String KEY_ARCHIVE_RETENTION_MINUTES = "archive_task_retention_minutes";
    public static final String KEY_ARCHIVE_MAX_CONCURRENT = "archive_task_max_concurrent";

    private static final String DEFAULT_TRANSPORT_MODE = "APPLICATION";
    private static final long DEFAULT_UPLOAD_PROXY_THRESHOLD_BYTES = 268435456L;
    private static final long DEFAULT_DOWNLOAD_PROXY_THRESHOLD_BYTES = 134217728L;
    private static final int DEFAULT_ARCHIVE_TASK_RETENTION_MINUTES = 120;
    private static final int DEFAULT_ARCHIVE_TASK_MAX_CONCURRENT = 2;
    private static final Set<String> ALLOWED_TRANSPORT_MODES = Set.of("APPLICATION", "PROXY", "DIRECT", "HYBRID");

    private final ConfigRepository configRepository;

    public ConfigService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public String getConfig(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(SysConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void setConfig(String key, String value, String description) {
        SysConfig config = configRepository.findByConfigKey(key)
                .orElse(new SysConfig());

        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);

        if (config.getId() == null) {
            config.setCreateTime(System.currentTimeMillis());
        } else {
            config.setUpdateTime(System.currentTimeMillis());
        }

        configRepository.save(config);
    }

    public Set<String> getAllowedExtensions() {
        String value = getConfig(KEY_ALLOWED_EXTENSIONS, "");
        if (value == null || value.isEmpty() || value.equals("无限制")) {
            return new HashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public Long getMaxFileSize() {
        String value = getConfig(KEY_MAX_FILE_SIZE, "-1");
        try {
            long size = Long.parseLong(value);
            return size < 0 ? -1 : size;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public Long getFrontendDownloadThresholdBytes() {
        String value = getConfig(KEY_FRONTEND_DOWNLOAD_THRESHOLD, "104857600");
        try {
            long size = Long.parseLong(value);
            return size <= 0 ? 104857600L : size;
        } catch (NumberFormatException e) {
            return 104857600L;
        }
    }

    @Transactional
    public void setAllowedExtensions(List<String> extensions) {
        String value = extensions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));
        setConfig(KEY_ALLOWED_EXTENSIONS, value, "允许上传的文件扩展名列表");
    }

    @Transactional
    public void setMaxFileSize(Long sizeInBytes) {
        setConfig(KEY_MAX_FILE_SIZE, String.valueOf(sizeInBytes), "最大文件大小，-1 表示不限制");
    }

    @Transactional
    public void setFrontendDownloadThresholdBytes(Long sizeInBytes) {
        setConfig(KEY_FRONTEND_DOWNLOAD_THRESHOLD, String.valueOf(sizeInBytes), "前端分块下载阈值，单位字节");
    }

    public Long getBatchShareMaxSize() {
        String value = getConfig(KEY_BATCH_SHARE_MAX_SIZE, "2147483648");
        try {
            long size = Long.parseLong(value);
            return size < 0 ? -1 : size;
        } catch (NumberFormatException e) {
            return 2147483648L;
        }
    }

    public Long getBatchDownloadMaxSize() {
        String value = getConfig(KEY_BATCH_DOWNLOAD_MAX_SIZE, "2147483648");
        try {
            long size = Long.parseLong(value);
            return size < 0 ? -1 : size;
        } catch (NumberFormatException e) {
            return 2147483648L;
        }
    }

    public Integer getShareExpireHours() {
        String value = getConfig(KEY_SHARE_EXPIRE_HOURS, "168");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 168;
        }
    }


    @Transactional
    public void setBatchShareMaxSize(Long sizeInBytes) {
        setConfig(KEY_BATCH_SHARE_MAX_SIZE, String.valueOf(sizeInBytes), "批量分享最大大小，-1 表示不限制");
    }

    @Transactional
    public void setBatchDownloadMaxSize(Long sizeInBytes) {
        setConfig(KEY_BATCH_DOWNLOAD_MAX_SIZE, String.valueOf(sizeInBytes), "批量下载最大大小，-1 表示不限制");
    }

    @Transactional
    public void setShareExpireHours(Integer hours) {
        setConfig(KEY_SHARE_EXPIRE_HOURS, String.valueOf(hours), "分享链接默认过期小时数");
    }

    public String getUploadTransportMode() {
        return normalizeTransportMode(getConfig(KEY_UPLOAD_TRANSPORT_MODE, DEFAULT_TRANSPORT_MODE));
    }

    public String getDownloadTransportMode() {
        return normalizeTransportMode(getConfig(KEY_DOWNLOAD_TRANSPORT_MODE, DEFAULT_TRANSPORT_MODE));
    }

    public Long getUploadProxyThresholdBytes() {
        String value = getConfig(KEY_UPLOAD_PROXY_THRESHOLD, String.valueOf(DEFAULT_UPLOAD_PROXY_THRESHOLD_BYTES));
        try {
            long size = Long.parseLong(value);
            return size <= 0 ? DEFAULT_UPLOAD_PROXY_THRESHOLD_BYTES : size;
        } catch (NumberFormatException e) {
            return DEFAULT_UPLOAD_PROXY_THRESHOLD_BYTES;
        }
    }

    public Long getDownloadProxyThresholdBytes() {
        String value = getConfig(KEY_DOWNLOAD_PROXY_THRESHOLD, String.valueOf(DEFAULT_DOWNLOAD_PROXY_THRESHOLD_BYTES));
        try {
            long size = Long.parseLong(value);
            return size <= 0 ? DEFAULT_DOWNLOAD_PROXY_THRESHOLD_BYTES : size;
        } catch (NumberFormatException e) {
            return DEFAULT_DOWNLOAD_PROXY_THRESHOLD_BYTES;
        }
    }

    public Integer getArchiveTaskRetentionMinutes() {
        String value = getConfig(KEY_ARCHIVE_RETENTION_MINUTES, String.valueOf(DEFAULT_ARCHIVE_TASK_RETENTION_MINUTES));
        try {
            int minutes = Integer.parseInt(value);
            return minutes <= 0 ? DEFAULT_ARCHIVE_TASK_RETENTION_MINUTES : minutes;
        } catch (NumberFormatException e) {
            return DEFAULT_ARCHIVE_TASK_RETENTION_MINUTES;
        }
    }

    public Integer getArchiveTaskMaxConcurrent() {
        String value = getConfig(KEY_ARCHIVE_MAX_CONCURRENT, String.valueOf(DEFAULT_ARCHIVE_TASK_MAX_CONCURRENT));
        try {
            int concurrent = Integer.parseInt(value);
            return concurrent <= 0 ? DEFAULT_ARCHIVE_TASK_MAX_CONCURRENT : concurrent;
        } catch (NumberFormatException e) {
            return DEFAULT_ARCHIVE_TASK_MAX_CONCURRENT;
        }
    }

    public boolean isAllowedTransportMode(String mode) {
        if (mode == null) {
            return false;
        }
        return ALLOWED_TRANSPORT_MODES.contains(mode.trim().toUpperCase());
    }

    @Transactional
    public void setUploadTransportMode(String mode) {
        if (isAllowedTransportMode(mode)) {
            setConfig(KEY_UPLOAD_TRANSPORT_MODE, mode.trim().toUpperCase(), "上传/下载的传输模式");
        }
    }

    @Transactional
    public void setDownloadTransportMode(String mode) {
        if (isAllowedTransportMode(mode)) {
            setConfig(KEY_DOWNLOAD_TRANSPORT_MODE, mode.trim().toUpperCase(), "下载传输模式");
        }
    }

    @Transactional
    public void setUploadProxyThresholdBytes(Long bytes) {
        if (bytes != null && bytes > 0) {
            setConfig(KEY_UPLOAD_PROXY_THRESHOLD, String.valueOf(bytes), "上传代理阈值，单位字节");
        }
    }

    @Transactional
    public void setDownloadProxyThresholdBytes(Long bytes) {
        if (bytes != null && bytes > 0) {
            setConfig(KEY_DOWNLOAD_PROXY_THRESHOLD, String.valueOf(bytes), "下载代理阈值，单位字节");
        }
    }

    @Transactional
    public void setArchiveTaskRetentionMinutes(Integer minutes) {
        if (minutes != null && minutes > 0) {
            setConfig(KEY_ARCHIVE_RETENTION_MINUTES, String.valueOf(minutes), "归档任务保留分钟数");
        }
    }

    @Transactional
    public void setArchiveTaskMaxConcurrent(Integer maxConcurrent) {
        if (maxConcurrent != null && maxConcurrent > 0) {
            setConfig(KEY_ARCHIVE_MAX_CONCURRENT, String.valueOf(maxConcurrent), "归档任务最大并发数");
        }
    }

    private String normalizeTransportMode(String mode) {
        if (mode == null) {
            return DEFAULT_TRANSPORT_MODE;
        }
        String normalized = mode.trim().toUpperCase();
        return ALLOWED_TRANSPORT_MODES.contains(normalized) ? normalized : DEFAULT_TRANSPORT_MODE;
    }
}
