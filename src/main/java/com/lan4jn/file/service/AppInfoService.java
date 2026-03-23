package com.lan4jn.file.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

@Service
public class AppInfoService {

    private static final String DEFAULT_VERSION = "dev";

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public String getCurrentVersion() {
        try (InputStream inputStream = resourcePatternResolver
                .getResource("classpath:app-info.properties")
                .getInputStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("app.version");
            return version == null || version.trim().isEmpty() ? DEFAULT_VERSION : version.trim();
        } catch (Exception e) {
            return DEFAULT_VERSION;
        }
    }

    public List<VersionNoteInfo> listVersionNotes() {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath*:version-notes/*.md");
            List<VersionNoteInfo> versions = new ArrayList<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".md")) {
                    continue;
                }
                String version = filename.substring(0, filename.length() - 3);
                versions.add(new VersionNoteInfo(version, filename));
            }
            versions.sort(Comparator.comparing(VersionNoteInfo::version, this::compareVersionDesc));
            return versions;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private int compareVersionDesc(String left, String right) {
        return compareVersion(right, left);
    }

    private int compareVersion(String left, String right) {
        int[] leftParts = normalizeVersion(left);
        int[] rightParts = normalizeVersion(right);
        int maxLength = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < maxLength; i++) {
            int leftValue = i < leftParts.length ? leftParts[i] : 0;
            int rightValue = i < rightParts.length ? rightParts[i] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return left.compareToIgnoreCase(right);
    }

    private int[] normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new int[0];
        }

        String[] parts = normalized.split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                values[i] = 0;
            }
        }
        return values;
    }

    public record VersionNoteInfo(String version, String fileName) {
    }
}

