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
    public static final String KEY_UPLOAD_TRANSPORT_PRIORITY = "upload_transport_priority";
    public static final String KEY_DOWNLOAD_TRANSPORT_PRIORITY = "download_transport_priority";
    public static final String KEY_UPLOAD_DIALOG_MODE = "upload_dialog_mode";
    public static final String KEY_FILE_DROP_MODE = "file_drop_mode";
    public static final String KEY_UPLOAD_PROXY_THRESHOLD = "upload_proxy_threshold_bytes";
    public static final String KEY_DOWNLOAD_PROXY_THRESHOLD = "download_proxy_threshold_bytes";
    public static final String KEY_ARCHIVE_RETENTION_MINUTES = "archive_task_retention_minutes";
    public static final String KEY_ARCHIVE_MAX_CONCURRENT = "archive_task_max_concurrent";
    public static final String KEY_FILE_ROW_HIDDEN_ACTIONS = "file_row_hidden_actions";

    private static final String DEFAULT_TRANSPORT_MODE = "APPLICATION";
    private static final String DEFAULT_UPLOAD_DIALOG_MODE = "INSTANT";
    private static final long DEFAULT_UPLOAD_PROXY_THRESHOLD_BYTES = 268435456L;
    private static final long DEFAULT_DOWNLOAD_PROXY_THRESHOLD_BYTES = 134217728L;
    private static final int DEFAULT_ARCHIVE_TASK_RETENTION_MINUTES = 120;
    private static final int DEFAULT_ARCHIVE_TASK_MAX_CONCURRENT = 2;
    private static final Set<String> ALLOWED_TRANSPORT_MODES = Set.of("APPLICATION", "PROXY", "DIRECT", "HYBRID");
    private static final List<String> ALLOWED_UPLOAD_TRANSPORT_PRIORITY = Arrays.asList("DIRECT", "APPLICATION");
    private static final List<String> ALLOWED_DOWNLOAD_TRANSPORT_PRIORITY = Arrays.asList("DIRECT", "PROXY", "APPLICATION");
    private static final Set<String> ALLOWED_UPLOAD_DIALOG_MODES = Set.of("MANUAL", "INSTANT");
    private static final List<String> DEFAULT_FILE_ROW_HIDDEN_ACTIONS = List.of("DELETE", "MOVE", "COPY");
    private static final Set<String> ALLOWED_FILE_ROW_ACTIONS = Set.of("DELETE", "MOVE", "COPY", "DOWNLOAD", "SHARE", "PREVIEW");

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

    public List<String> getUploadTransportPriority() {
        String raw = getConfig(KEY_UPLOAD_TRANSPORT_PRIORITY, "");
        List<String> parsed = normalizePriorityList(raw, ALLOWED_UPLOAD_TRANSPORT_PRIORITY);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return mapLegacyUploadPriority(getUploadTransportMode());
    }

    public List<String> getDownloadTransportPriority() {
        String raw = getConfig(KEY_DOWNLOAD_TRANSPORT_PRIORITY, "");
        List<String> parsed = normalizePriorityList(raw, ALLOWED_DOWNLOAD_TRANSPORT_PRIORITY);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        return mapLegacyDownloadPriority(getDownloadTransportMode());
    }

    public String getUploadDialogMode() {
        String raw = getConfig(KEY_UPLOAD_DIALOG_MODE, DEFAULT_UPLOAD_DIALOG_MODE);
        return normalizeUploadTriggerMode(raw);
    }

    public String getFileDropMode() {
        String raw = getConfig(KEY_FILE_DROP_MODE, DEFAULT_UPLOAD_DIALOG_MODE);
        return normalizeUploadTriggerMode(raw);
    }

    private String normalizeUploadTriggerMode(String raw) {
        if (raw == null) {
            return DEFAULT_UPLOAD_DIALOG_MODE;
        }
        String normalized = raw.trim().toUpperCase();
        return ALLOWED_UPLOAD_DIALOG_MODES.contains(normalized) ? normalized : DEFAULT_UPLOAD_DIALOG_MODE;
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

    public List<String> getFileRowHiddenActions() {
        String raw = getConfig(KEY_FILE_ROW_HIDDEN_ACTIONS, String.join(",", DEFAULT_FILE_ROW_HIDDEN_ACTIONS));
        List<String> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .filter(ALLOWED_FILE_ROW_ACTIONS::contains)
                .distinct()
                .toList();
        return parsed.isEmpty() ? DEFAULT_FILE_ROW_HIDDEN_ACTIONS : parsed;
    }

    @Transactional
    public void setFileRowHiddenActions(List<String> actions) {
        List<String> normalized = (actions == null ? List.<String>of() : actions).stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .filter(ALLOWED_FILE_ROW_ACTIONS::contains)
                .distinct()
                .toList();
        setConfig(KEY_FILE_ROW_HIDDEN_ACTIONS,
                String.join(",", normalized.isEmpty() ? DEFAULT_FILE_ROW_HIDDEN_ACTIONS : normalized),
                "文件列表默认收纳到更多菜单的操作按钮");
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

    public boolean isAllowedUploadDialogMode(String mode) {
        if (mode == null) {
            return false;
        }
        return ALLOWED_UPLOAD_DIALOG_MODES.contains(mode.trim().toUpperCase());
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
    public void setUploadTransportPriority(List<String> modes) {
        List<String> normalized = normalizePriorityList(modes, ALLOWED_UPLOAD_TRANSPORT_PRIORITY);
        if (!normalized.isEmpty()) {
            setConfig(KEY_UPLOAD_TRANSPORT_PRIORITY, String.join(",", normalized), "上传传输机制优先级");
            setConfig(KEY_UPLOAD_TRANSPORT_MODE, normalized.get(0), "上传/下载的传输模式");
        }
    }

    @Transactional
    public void setDownloadTransportPriority(List<String> modes) {
        List<String> normalized = normalizePriorityList(modes, ALLOWED_DOWNLOAD_TRANSPORT_PRIORITY);
        if (!normalized.isEmpty()) {
            setConfig(KEY_DOWNLOAD_TRANSPORT_PRIORITY, String.join(",", normalized), "下载传输机制优先级");
            setConfig(KEY_DOWNLOAD_TRANSPORT_MODE, normalized.get(0), "下载传输模式");
        }
    }

    @Transactional
    public void setUploadDialogMode(String mode) {
        if (isAllowedUploadDialogMode(mode)) {
            setConfig(KEY_UPLOAD_DIALOG_MODE, mode.trim().toUpperCase(), "上传弹窗模式");
        }
    }

    @Transactional
    public void setFileDropMode(String mode) {
        if (isAllowedUploadDialogMode(mode)) {
            setConfig(KEY_FILE_DROP_MODE, mode.trim().toUpperCase(), "文件列表拖拽上传模式");
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

    private List<String> normalizePriorityList(String raw, List<String> allowed) {
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return normalizePriorityList(Arrays.asList(raw.split(",")), allowed);
    }

    private List<String> normalizePriorityList(List<String> raw, List<String> allowed) {
        if (raw == null) {
            return java.util.Collections.emptyList();
        }
        return raw.stream()
                .map(item -> item == null ? "" : item.trim().toUpperCase())
                .map(item -> "PROXY".equals(item) && allowed == ALLOWED_UPLOAD_TRANSPORT_PRIORITY ? "APPLICATION" : item)
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> mapLegacyUploadPriority(String mode) {
        String normalized = normalizeTransportMode(mode);
        if ("DIRECT".equals(normalized)) {
            return Arrays.asList("DIRECT", "APPLICATION");
        }
        if ("HYBRID".equals(normalized)) {
            return Arrays.asList("DIRECT", "APPLICATION");
        }
        return java.util.Collections.singletonList("APPLICATION");
    }

    private List<String> mapLegacyDownloadPriority(String mode) {
        String normalized = normalizeTransportMode(mode);
        if ("DIRECT".equals(normalized)) {
            return Arrays.asList("DIRECT", "PROXY", "APPLICATION");
        }
        if ("PROXY".equals(normalized)) {
            return Arrays.asList("PROXY", "APPLICATION");
        }
        if ("HYBRID".equals(normalized)) {
            return Arrays.asList("PROXY", "APPLICATION");
        }
        return Arrays.asList("APPLICATION", "PROXY");
    }
}
