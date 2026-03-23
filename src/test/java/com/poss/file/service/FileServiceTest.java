package com.poss.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileServiceTest {

    @Test
    void shouldOnlyExposeLeafDirectoryNameForFolderObject() {
        FileService fileService = new FileService(null, null);

        String fileName = ReflectionTestUtils.invokeMethod(fileService, "getFileName", "docs/reports/2026/");

        assertEquals("2026", fileName);
    }
}
