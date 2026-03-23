package com.poss.file.controller;

import com.poss.file.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${ui.mode:full}")
    private String uiMode;

    @Value("${server.port:8080}")
    private int serverPort;

    @GetMapping("/ui")
    public Result<Map<String, Object>> getUiConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", uiMode);
        config.put("port", serverPort);
        return Result.success(config);
    }
}
