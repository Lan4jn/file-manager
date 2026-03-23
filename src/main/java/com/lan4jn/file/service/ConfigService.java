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
    public static final String KEY_BATCH_SHARE_MAX_SIZE = "batch_share_max_size";
    public static final String KEY_BATCH_DOWNLOAD_MAX_SIZE = "batch_download_max_size";
    public static final String KEY_SHARE_EXPIRE_HOURS = "share_expire_hours";

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
}
