package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;
    private PermissionAssertService permissionAssertService;
    private ConfigService configService;
    private OperationLogService operationLogService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        configService = mock(ConfigService.class);
        permissionAssertService = mock(PermissionAssertService.class);
        operationLogService = mock(OperationLogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, configService, permissionAssertService, operationLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeSinglePublicConstructorForSpringInjection() {
        long publicConstructorCount = Arrays.stream(FileController.class.getConstructors())
                .filter(constructor -> constructor.getDeclaringClass().equals(FileController.class))
                .count();

        assertEquals(1L, publicConstructorCount);
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
        BucketInfo allowedA = new BucketInfo();
        allowedA.setName("bucket-a");
        BucketInfo allowedB = new BucketInfo();
        allowedB.setName("bucket-b");
        when(fileService.listBuckets(argThat(buckets ->
                buckets != null
                        && buckets.size() == 2
                        && buckets.contains("bucket-a")
                        && buckets.contains("bucket-b")
        ), eq("name"), eq("asc")))
                .thenReturn(List.of(allowedA, allowedB));
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                2L, "user", false, Set.of("USER"), Set.of(), Set.of(),
                Map.of("bucket-a", Set.of("DOWNLOAD"), "bucket-b", Set.of("DOWNLOAD"))
        ));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"))
                .andExpect(jsonPath("$.data[1].name").value("bucket-b"));

        verify(fileService).listBuckets(argThat(buckets ->
                buckets != null
                        && buckets.size() == 2
                        && buckets.contains("bucket-a")
                        && buckets.contains("bucket-b")
        ), eq("name"), eq("asc"));
        verify(fileService, never()).listBuckets("name", "asc");
    }

    @Test
    void shouldLoadBucketWhenBucketPermissionEntryExistsWithoutActions() throws Exception {
        BucketInfo allowed = new BucketInfo();
        allowed.setName("bucket-a");
        when(fileService.listBuckets(argThat(buckets ->
                buckets != null
                        && buckets.size() == 1
                        && buckets.contains("bucket-a")
        ), eq("name"), eq("asc")))
                .thenReturn(List.of(allowed));
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                2L, "user", false, Set.of("USER"), Set.of(), Set.of(),
                Map.of("bucket-a", Set.of())
        ));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"));

        verify(fileService).listBuckets(argThat(buckets ->
                buckets != null
                        && buckets.size() == 1
                        && buckets.contains("bucket-a")
        ), eq("name"), eq("asc"));
        verify(fileService, never()).listBuckets("name", "asc");
    }

    @Test
    void shouldLoadAllBucketsWhenWildcardBucketPermissionExists() throws Exception {
        BucketInfo allowed = new BucketInfo();
        allowed.setName("bucket-a");
        when(fileService.listBuckets("name", "asc")).thenReturn(List.of(allowed));
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                2L, "user", false, Set.of("USER"), Set.of(), Set.of(),
                Map.of("*", Set.of("DOWNLOAD"))
        ));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"));

        verify(fileService).listBuckets("name", "asc");
        verify(fileService, never()).listBuckets(eq(List.of("bucket-a")), eq("name"), eq("asc"));
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
    void shouldReturnUploadConfigForFileOperators() throws Exception {
        when(configService.getAllowedExtensions()).thenReturn(Set.of("txt", "pdf"));
        when(configService.getMaxFileSize()).thenReturn(2048L);

        mockMvc.perform(get("/api/file/upload-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.maxFileSize").value(2048))
                .andExpect(jsonPath("$.data.allowedExtensions.length()").value(2));

        verify(permissionAssertService).assertApiPermission("API_FILE_OPERATE");
    }

    @Test
    void shouldReturnDownloadResponseHeadersForSingleFile() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setContentType("text/plain");
        fileInfo.setSize(12L);

        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(fileService.getFileStream("docs/readme.txt", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/file/download")
                        .param("objectName", "docs/readme.txt")
                        .param("bucket", "bucket-a"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"readme.txt\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 12L));

        verify(fileService).getFileInfo("docs/readme.txt", "bucket-a");
        verify(fileService).getFileStream("docs/readme.txt", "bucket-a");
    }

    @Test
    void shouldSupportRangeDownloadForSingleFile() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setContentType("text/plain");
        fileInfo.setSize(12L);

        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(fileService.getFileStream("docs/readme.txt", "bucket-a", 6L, 6L))
                .thenReturn(new ByteArrayInputStream("world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/file/download")
                        .param("objectName", "docs/readme.txt")
                        .param("bucket", "bucket-a")
                        .header(HttpHeaders.RANGE, "bytes=6-11"))
                .andExpect(status().is(HttpStatus.PARTIAL_CONTENT.value()))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 6-11/12"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6L))
                .andExpect(content().string("world!"));
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

    @Test
    void shouldRecordAuditLogWhenCreateFolderSucceeds() throws Exception {
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                1L, "admin", true, Set.of("ADMIN"), Set.of(), Set.of(), Map.of()
        ));
        mockMvc.perform(get("/api/file/info").param("objectName", "docs/readme.txt"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/file/folder")
                        .param("path", "docs/new-folder")
                        .param("bucket", "bucket-a")
                        .param("createTestFile", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fileService).createFolder("docs/new-folder", "bucket-a", false);
        verify(operationLogService).safeRecord(
                eq("admin"),
                eq("FILE"),
                eq("CREATE_FOLDER"),
                eq("FOLDER"),
                eq("docs/new-folder"),
                eq("docs/new-folder"),
                eq(true),
                eq(null),
                eq("bucket-a"),
                eq("docs/new-folder"),
                any()
        );
    }
}
