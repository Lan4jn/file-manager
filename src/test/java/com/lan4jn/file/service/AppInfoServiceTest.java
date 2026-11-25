package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppInfoServiceTest {

    private static final String CURRENT_BUILD = "build152";

    private final AppInfoService appInfoService = new AppInfoService();

    @Test
    void shouldReadCurrentVersionFromBuildMetadata() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("app-info.properties")) {
            properties.load(inputStream);
        }

        assertEquals("1.9-" + CURRENT_BUILD, properties.getProperty("app.version"));
        assertEquals(properties.getProperty("app.version"), appInfoService.getCurrentVersion());
    }

    @Test
    void shouldDocumentCurrentBuildAsLatestVersionNote() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/version-notes/v1.9.md")) {
            assertNotNull(inputStream);
            String latestBuildHeading = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("## build"))
                    .findFirst().orElse(null);
            assertEquals("## " + CURRENT_BUILD, latestBuildHeading);
        }
    }

    @Test
    void shouldListVersionNotesInDescendingOrder() {
        List<AppInfoService.VersionNoteInfo> versions = appInfoService.listVersionNotes();

        assertFalse(versions.isEmpty());
        List<String> sorted = versions.stream()
                .map(AppInfoService.VersionNoteInfo::version)
                .sorted(Comparator.reverseOrder())
                .toList();
        assertEquals(sorted.get(0), versions.get(0).version());
        assertEquals(versions.get(0).version() + ".md", versions.get(0).fileName());
    }
}
