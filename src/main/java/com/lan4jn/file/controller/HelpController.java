package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.service.AppInfoService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/help")
public class HelpController {

    private final AppInfoService appInfoService;

    public HelpController(AppInfoService appInfoService) {
        this.appInfoService = appInfoService;
    }

    @GetMapping("/system")
    public Result<String> getSystemHelp() {
        try {
            ClassPathResource resource = new ClassPathResource("help.md");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return Result.success(content);
        } catch (Exception e) {
            return Result.error("鑾峰彇甯姪鏂囨。澶辫触: " + e.getMessage());
        }
    }

    @GetMapping("/versions")
    public Result<List<Map<String, String>>> getVersionList() {
        List<Map<String, String>> versions = new ArrayList<>();
        for (AppInfoService.VersionNoteInfo versionNote : appInfoService.listVersionNotes()) {
            Map<String, String> versionInfo = new HashMap<>();
            versionInfo.put("version", versionNote.version());
            versionInfo.put("fileName", versionNote.fileName());
            versions.add(versionInfo);
        }
        return Result.success(versions);
    }

    @GetMapping("/version")
    public Result<String> getVersionNote(@RequestParam("fileName") String fileName) {
        try {
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return Result.error("鏃犳晥鐨勬枃浠跺悕");
            }

            ClassPathResource resource = new ClassPathResource("version-notes/" + fileName);
            if (!resource.exists()) {
                return Result.error("鐗堟湰鏂囦欢涓嶅瓨鍦?");
            }

            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return Result.success(content);
        } catch (Exception e) {
            return Result.error("鑾峰彇鐗堟湰鏇存柊鍐呭澶辫触: " + e.getMessage());
        }
    }
}

