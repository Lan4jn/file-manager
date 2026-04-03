package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppInfoServiceTest {

    private final AppInfoService appInfoService = new AppInfoService();

    @Test
    void shouldReadCurrentVersionFromBuildMetadata() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("app-info.properties")) {
            properties.load(inputStream);
        }

        assertEquals(properties.getProperty("app.version"), appInfoService.getCurrentVersion());
    }

    @Test
    void shouldListVersionNotesInDescendingOrder() {
        List<AppInfoService.VersionNoteInfo> versions = appInfoService.listVersionNotes();

        assertFalse(versions.isEmpty());
        assertEquals("v1.6.1", versions.get(0).version());
        assertEquals("v1.6.1.md", versions.get(0).fileName());
    }
}
