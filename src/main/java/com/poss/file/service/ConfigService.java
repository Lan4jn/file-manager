package com.poss.file.service;

import com.poss.file.entity.SysConfig;
import com.poss.file.repository.ConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConfigService {

    @Autowired
    private ConfigRepository configRepository;

    public static final String KEY_ALLOWED_EXTENSIONS = "allowed_extensions";
    public static final String KEY_MAX_FILE_SIZE = "max_file_size";
    public static final String KEY_SHARE_MAX_SIZE = "share_max_size";
    public static final String KEY_SHARE_EXPIRE_HOURS = "share_expire_hours";

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
        String value = getConfig(KEY_ALLOWED_EXTENSIONS,
                "jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,mp3,mp4,avi,zip,rar,jar");
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public Long getMaxFileSize() {
        String value = getConfig(KEY_MAX_FILE_SIZE, "104857600"); // 100MB default
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 104857600L;
        }
    }

    @Transactional
    public void setAllowedExtensions(List<String> extensions) {
        String value = extensions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));
        setConfig(KEY_ALLOWED_EXTENSIONS, value, "允许上传的文件扩展名");
    }

    @Transactional
    public void setMaxFileSize(Long sizeInBytes) {
        setConfig(KEY_MAX_FILE_SIZE, String.valueOf(sizeInBytes), "最大文件大小(字节)");
    }

    public Long getShareMaxSize() {
        String value = getConfig(KEY_SHARE_MAX_SIZE, "2147483648"); // 2GB default
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 2147483648L;
        }
    }

    public Integer getShareExpireHours() {
        String value = getConfig(KEY_SHARE_EXPIRE_HOURS, "168"); // 7 days default
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 168;
        }
    }

    @Transactional
    public void setShareMaxSize(Long sizeInBytes) {
        setConfig(KEY_SHARE_MAX_SIZE, String.valueOf(sizeInBytes), "分享目录最大大小(字节)");
    }

    @Transactional
    public void setShareExpireHours(Integer hours) {
        setConfig(KEY_SHARE_EXPIRE_HOURS, String.valueOf(hours), "分享链接有效期(小时)");
    }
}
