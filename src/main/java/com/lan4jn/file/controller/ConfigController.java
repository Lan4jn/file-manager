package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.service.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    @Value("${ui.mode:full}")
    private String uiMode;

    @Value("${server.port:8080}")
    private int serverPort;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/ui")
    public Result<Map<String, Object>> getUiConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", uiMode);
        config.put("port", serverPort);
        config.put("uploadTransportMode", configService.getUploadTransportMode());
        config.put("downloadTransportMode", configService.getDownloadTransportMode());
        config.put("uploadTransportPriority", configService.getUploadTransportPriority());
        config.put("downloadTransportPriority", configService.getDownloadTransportPriority());
        config.put("uploadDialogMode", configService.getUploadDialogMode());
        config.put("fileDropMode", configService.getFileDropMode());
        config.put("fileRowHiddenActions", configService.getFileRowHiddenActions());
        return Result.success(config);
    }
}
