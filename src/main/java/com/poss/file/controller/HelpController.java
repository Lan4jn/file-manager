package com.poss.file.controller;

import com.poss.file.common.Result;
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

    @GetMapping("/system")
    public Result<String> getSystemHelp() {
        try {
            ClassPathResource resource = new ClassPathResource("help.md");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return Result.success(content);
        } catch (Exception e) {
            return Result.error("获取帮助文档失败: " + e.getMessage());
        }
    }

    @GetMapping("/versions")
    public Result<List<Map<String, String>>> getVersionList() {
        List<Map<String, String>> versions = new ArrayList<>();

        // 版本列表，按倒序排列（最新版本在前）
        String[] versionFiles = {
                "v1.3-dev.md",
                "v1.2.1.md",
                "v1.2.md",
                "v1.1.md",
                "v1.0.md"
        };

        for (String fileName : versionFiles) {
            String version = fileName.replace(".md", "");
            Map<String, String> versionInfo = new HashMap<>();
            versionInfo.put("version", version);
            versionInfo.put("fileName", fileName);
            versions.add(versionInfo);
        }

        return Result.success(versions);
    }

    @GetMapping("/version")
    public Result<String> getVersionNote(@RequestParam("fileName") String fileName) {
        try {
            // 防止路径遍历攻击
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return Result.error("无效的文件名");
            }

            ClassPathResource resource = new ClassPathResource("version-notes/" + fileName);
            if (!resource.exists()) {
                return Result.error("版本文件不存在");
            }

            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return Result.success(content);
        } catch (Exception e) {
            return Result.error("获取版本更新内容失败: " + e.getMessage());
        }
    }
}
