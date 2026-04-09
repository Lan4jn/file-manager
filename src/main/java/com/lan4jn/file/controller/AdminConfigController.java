package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.PermissionAssertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final ConfigService configService;
    private final PermissionAssertService permissionAssertService;

    public AdminConfigController(ConfigService configService, PermissionAssertService permissionAssertService) {
        this.configService = configService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/file-config")
    public Result<Map<String, Object>> getFileConfig() {
        ensureSuperAdmin();
        Set<String> extensions = configService.getAllowedExtensions();
        Long maxSize = configService.getMaxFileSize();
        Long frontendDownloadThreshold = configService.getFrontendDownloadThresholdBytes();
        Map<String, Object> config = new HashMap<>();
        config.put("allowedExtensions", extensions);
        config.put("maxFileSize", maxSize);
        config.put("frontendDownloadThresholdMB", frontendDownloadThreshold / 1024 / 1024);
        config.put("uploadTransportMode", configService.getUploadTransportMode());
        config.put("downloadTransportMode", configService.getDownloadTransportMode());
        config.put("uploadProxyThresholdMB", configService.getUploadProxyThresholdBytes() / 1024 / 1024);
        config.put("downloadProxyThresholdMB", configService.getDownloadProxyThresholdBytes() / 1024 / 1024);
        config.put("archiveTaskRetentionMinutes", configService.getArchiveTaskRetentionMinutes());
        config.put("archiveTaskMaxConcurrent", configService.getArchiveTaskMaxConcurrent());
        return Result.success(config);
    }

    @PostMapping("/file-config")
    public Result<Void> updateFileConfig(@RequestBody Map<String, Object> configData) {
        ensureSuperAdmin();
        if (configData.containsKey("allowedExtensions")) {
            List<String> extensions = (List<String>) configData.get("allowedExtensions");
            configService.setAllowedExtensions(extensions);
        }
        if (configData.containsKey("maxFileSize")) {
            Long maxSize = Long.valueOf(configData.get("maxFileSize").toString());
            configService.setMaxFileSize(maxSize);
        }
        if (configData.containsKey("frontendDownloadThresholdMB")) {
            Long thresholdMb = Long.valueOf(configData.get("frontendDownloadThresholdMB").toString());
            configService.setFrontendDownloadThresholdBytes(thresholdMb * 1024L * 1024L);
        }
        if (configData.containsKey("uploadTransportMode")) {
            Object raw = configData.get("uploadTransportMode");
            if (raw != null) {
                String mode = raw.toString();
                if (configService.isAllowedTransportMode(mode)) {
                    configService.setUploadTransportMode(mode);
                }
            }
        }
        if (configData.containsKey("downloadTransportMode")) {
            Object raw = configData.get("downloadTransportMode");
            if (raw != null) {
                String mode = raw.toString();
                if (configService.isAllowedTransportMode(mode)) {
                    configService.setDownloadTransportMode(mode);
                }
            }
        }
        if (configData.containsKey("uploadProxyThresholdMB")) {
            Object raw = configData.get("uploadProxyThresholdMB");
            if (raw != null) {
                Long thresholdMb = Long.valueOf(raw.toString());
                if (thresholdMb > 0) {
                    configService.setUploadProxyThresholdBytes(thresholdMb * 1024L * 1024L);
                }
            }
        }
        if (configData.containsKey("downloadProxyThresholdMB")) {
            Object raw = configData.get("downloadProxyThresholdMB");
            if (raw != null) {
                Long thresholdMb = Long.valueOf(raw.toString());
                if (thresholdMb > 0) {
                    configService.setDownloadProxyThresholdBytes(thresholdMb * 1024L * 1024L);
                }
            }
        }
        if (configData.containsKey("archiveTaskRetentionMinutes")) {
            Object raw = configData.get("archiveTaskRetentionMinutes");
            if (raw != null) {
                Integer minutes = Integer.valueOf(raw.toString());
                if (minutes > 0) {
                    configService.setArchiveTaskRetentionMinutes(minutes);
                }
            }
        }
        if (configData.containsKey("archiveTaskMaxConcurrent")) {
            Object raw = configData.get("archiveTaskMaxConcurrent");
            if (raw != null) {
                Integer maxConcurrent = Integer.valueOf(raw.toString());
                if (maxConcurrent > 0) {
                    configService.setArchiveTaskMaxConcurrent(maxConcurrent);
                }
            }
        }
        return Result.success();
    }

    @GetMapping("/share-config")
    public Result<Map<String, Object>> getShareConfig() {
        ensureSuperAdmin();
        Long batchShareMaxSize = configService.getBatchShareMaxSize();
        Long batchDownloadMaxSize = configService.getBatchDownloadMaxSize();
        Integer shareExpireHours = configService.getShareExpireHours();
        Map<String, Object> config = new HashMap<>();
        config.put("batchShareMaxSize", batchShareMaxSize);
        config.put("batchDownloadMaxSize", batchDownloadMaxSize);
        config.put("shareExpireHours", shareExpireHours);
        return Result.success(config);
    }

    @PostMapping("/share-config")
    public Result<Void> updateShareConfig(@RequestBody Map<String, Object> configData) {
        ensureSuperAdmin();
        if (configData.containsKey("batchShareMaxSize")) {
            Long maxSize = Long.valueOf(configData.get("batchShareMaxSize").toString());
            configService.setBatchShareMaxSize(maxSize);
        }
        if (configData.containsKey("batchDownloadMaxSize")) {
            Long maxSize = Long.valueOf(configData.get("batchDownloadMaxSize").toString());
            configService.setBatchDownloadMaxSize(maxSize);
        }
        if (configData.containsKey("shareExpireHours")) {
            Integer hours = Integer.valueOf(configData.get("shareExpireHours").toString());
            configService.setShareExpireHours(hours);
        }
        return Result.success();
    }

    private void ensureSuperAdmin() {
        if (!permissionAssertService.isSuperAdmin()) {
            throw new SecurityException("系统配置仅超级管理员可访问");
        }
    }
}
