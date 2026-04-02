package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;
    private PermissionAssertService permissionAssertService;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        configService = mock(ConfigService.class);
        permissionAssertService = mock(PermissionAssertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, configService, permissionAssertService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnBucketList() throws Exception {
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setName("bucket-a");
        bucketInfo.setCreationDate(LocalDateTime.now());

        when(fileService.listBuckets("name", "asc")).thenReturn(List.of(bucketInfo));
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                1L, "admin", true, Set.of("ADMIN"), Set.of(), Set.of(), Map.of()
        ));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"));
    }

    @Test
    void shouldLoadOnlyAuthorizedBuckets() throws Exception {
        BucketInfo allowed = new BucketInfo();
        allowed.setName("bucket-a");
        when(fileService.listBuckets(List.of("bucket-a"), "name", "asc")).thenReturn(List.of(allowed));
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                2L, "user", false, Set.of("USER"), Set.of(), Set.of(),
                Map.of("bucket-a", Set.of("LIST_BUCKET"), "bucket-b", Set.of("DOWNLOAD"))
        ));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"));

        verify(fileService).listBuckets(eq(List.of("bucket-a")), eq("name"), eq("asc"));
        verify(fileService, never()).listBuckets("name", "asc");
    }

    @Test
    void shouldReturnFileListForNonPagedRequest() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setIsFolder(false);

        when(fileService.listFiles("", "", "name", "asc")).thenReturn(List.of(fileInfo));

        mockMvc.perform(get("/api/file/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("readme.txt"));
    }

    @Test
    void shouldReturnBadRequestWhenObjectNameIsBlank() throws Exception {
        mockMvc.perform(get("/api/file/info").param("objectName", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("objectName不能为空"));
    }

    @Test
    void shouldRejectBatchDownloadBeforeStartingStreamWhenSizeExceedsLimit() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setSize(20L);

        when(fileService.resolveBatchArchiveEntries(List.of("docs/readme.txt"), "")).thenReturn(List.of(fileInfo));
        when(configService.getBatchDownloadMaxSize()).thenReturn(10L);

        mockMvc.perform(get("/api/file/batch-download").param("objectNames", "docs/readme.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("批量下载总大小超过限制(10 B)"));

        verify(fileService, never()).downloadResolvedFilesAsZip(eq(List.of(fileInfo)), eq(""), any());
    }
}
