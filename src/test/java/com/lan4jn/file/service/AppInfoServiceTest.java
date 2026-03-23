package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppInfoServiceTest {

    private final AppInfoService appInfoService = new AppInfoService();

    @Test
    void shouldReadCurrentVersionFromBuildMetadata() {
        assertEquals("1.3.2", appInfoService.getCurrentVersion());
    }

    @Test
    void shouldListVersionNotesInDescendingOrder() {
        List<AppInfoService.VersionNoteInfo> versions = appInfoService.listVersionNotes();

        assertFalse(versions.isEmpty());
        assertEquals("v1.3.2", versions.get(0).version());
        assertEquals("v1.3.2.md", versions.get(0).fileName());
    }
}

