package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.service.ConfigService;
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

    public AdminConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/file-config")
    public Result<Map<String, Object>> getFileConfig() {
        Set<String> extensions = configService.getAllowedExtensions();
        Long maxSize = configService.getMaxFileSize();

        Map<String, Object> config = new HashMap<>();
        config.put("allowedExtensions", extensions);
        config.put("maxFileSize", maxSize);
        return Result.success(config);
    }

    @PostMapping("/file-config")
    public Result<Void> updateFileConfig(@RequestBody Map<String, Object> configData) {
        if (configData.containsKey("allowedExtensions")) {
            List<String> extensions = (List<String>) configData.get("allowedExtensions");
            configService.setAllowedExtensions(extensions);
        }

        if (configData.containsKey("maxFileSize")) {
            Long maxSize = Long.valueOf(configData.get("maxFileSize").toString());
            configService.setMaxFileSize(maxSize);
        }

        return Result.success();
    }

    @GetMapping("/share-config")
    public Result<Map<String, Object>> getShareConfig() {
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
}
