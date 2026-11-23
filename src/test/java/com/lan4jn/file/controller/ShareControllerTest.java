package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.ShareArchivePlan;
import com.lan4jn.file.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareControllerTest {

    private MockMvc mockMvc;
    private ShareService shareService;
    private OperationLogService operationLogService;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        shareService = mock(ShareService.class);
        FileService fileService = mock(FileService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        operationLogService = mock(OperationLogService.class);
        configService = mock(ConfigService.class);
        when(configService.getFrontendDownloadThresholdBytes()).thenReturn(104857600L);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileService, permissionAssertService, operationLogService, configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeSinglePublicConstructorForSpringInjection() {
        long publicConstructorCount = Arrays.stream(ShareController.class.getConstructors())
                .filter(constructor -> constructor.getDeclaringClass().equals(ShareController.class))
                .count();

        assertEquals(1L, publicConstructorCount);
    }

    @Test
    void shouldReturnShareAccessInfo() throws Exception {
        ShareAccessInfo info = new ShareAccessInfo();
        info.setShareCode("SHARE001");
        info.setFileName("docs");
        info.setIsFolder(true);
        info.setExpireTime(LocalDateTime.now().plusHours(1));

        when(shareService.getShareAccessInfo("SHARE001")).thenReturn(info);

        mockMvc.perform(get("/api/share/access-info").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareCode").value("SHARE001"))
                .andExpect(jsonPath("$.data.fileName").value("docs"))
                .andExpect(jsonPath("$.data.frontendDownloadThresholdMB").value(100));
    }

    @Test
    void shouldReturnSharedItems() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setIsFolder(false);

        when(shareService.listSharedItems("SHARE001", null, null)).thenReturn(List.of(fileInfo));

        mockMvc.perform(get("/api/share/items").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("readme.txt"));
    }

    @Test
    void shouldReturnPagedShareListWithValidOnly() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("ACTIVE01");
        shareLink.setFileName("readme.txt");
        when(shareService.getShareLinksByUser("admin", 2, 10, true))
                .thenReturn(new PageResult<>(List.of(shareLink), 11, 2, 10));

        mockMvc.perform(get("/api/share/list")
                        .param("page", "2")
                        .param("size", "10")
                        .param("validOnly", "true")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.items[0].shareCode").value("ACTIVE01"));
    }

    @Test
    void shouldReturnBadRequestWhenShareCodeIsBlank() throws Exception {
        mockMvc.perform(get("/api/share/access-info").param("shareCode", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("shareCode不能为空"));
    }

    @Test
    void shouldReturnDownloadResponseHeadersForSharedFile() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setObjectName("docs/readme.txt");
        shareLink.setBucket("bucket-a");
        shareLink.setIsFolder(false);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setContentType("text/plain");
        fileInfo.setSize(12L);

        FileService fileService = mock(FileService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileService, permissionAssertService, operationLogService, configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(fileService.getFileStream("docs/readme.txt", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/share/download").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"readme.txt\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 12L));

        verify(shareService).incrementViewCount("SHARE001");
        verify(fileService).getFileInfo("docs/readme.txt", "bucket-a");
        verify(fileService).getFileStream("docs/readme.txt", "bucket-a");
    }

    @Test
    void shouldSupportRangeDownloadForSharedFile() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setObjectName("docs/readme.txt");
        shareLink.setBucket("bucket-a");
        shareLink.setIsFolder(false);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setContentType("text/plain");
        fileInfo.setSize(12L);

        FileService fileService = mock(FileService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileService, permissionAssertService, operationLogService, configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(fileService.getFileStream("docs/readme.txt", "bucket-a", 6L, 6L))
                .thenReturn(new ByteArrayInputStream("world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/share/download")
                        .param("shareCode", "SHARE001")
                        .header(HttpHeaders.RANGE, "bytes=6-11"))
                .andExpect(status().is(HttpStatus.PARTIAL_CONTENT.value()))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 6-11/12"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6L))
                .andExpect(content().string("world!"));
    }

    @Test
    void shouldNotIncrementViewCountWhenPrepareShareArchiveFails() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setIsFolder(true);

        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);
        when(shareService.prepareShareArchive("SHARE001", null)).thenThrow(new RuntimeException("分享下载失败"));

        mockMvc.perform(get("/api/share/download-all").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分享下载失败"));

        verify(shareService, never()).incrementViewCount("SHARE001");
    }

    @Test
    void shouldRecordAuditLogWhenDownloadAllSharedItemsSucceeds() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setFileName("docs");
        shareLink.setBucket("bucket-a");
        shareLink.setObjectName("docs/");
        shareLink.setIsFolder(true);

        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);
        when(shareService.prepareShareArchive("SHARE001", null))
                .thenReturn(new ShareArchivePlan(shareLink, new LinkedHashMap<>()));
        when(shareService.getShareArchiveName("SHARE001", null)).thenReturn("docs.zip");
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
                1L, "admin", true, Set.of("ADMIN"), Set.of(), Set.of(), Map.of()
        ));
        FileService fileService = mock(FileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileService, permissionAssertService, operationLogService, configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/share/download-all").param("shareCode", "SHARE001"))
                .andExpect(status().isOk());

        verify(operationLogService).safeRecord(
                org.mockito.Mockito.eq("admin"),
                org.mockito.Mockito.eq("SHARE"),
                org.mockito.Mockito.eq("DOWNLOAD_ALL"),
                org.mockito.Mockito.eq("SHARE"),
                org.mockito.Mockito.eq("SHARE001"),
                org.mockito.Mockito.eq("docs"),
                org.mockito.Mockito.eq(true),
                org.mockito.Mockito.eq(null),
                org.mockito.Mockito.eq("bucket-a"),
                org.mockito.Mockito.eq("docs/"),
                any()
        );
    }

    @Test
    void shouldPassUserTargetTypeAndRecipientsToBatchShareService() throws Exception {
        FileService fileService = mock(FileService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        ShareController controller = new ShareController(shareService, fileService, permissionAssertService, operationLogService, configService);
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("BATCH001");
        shareLink.setTargetType("USER");
        shareLink.setHasPassword(false);
        when(shareService.createBatchShareLink(
                org.mockito.ArgumentMatchers.<List<String>>argThat(objectNames -> objectNames.size() == 2 && objectNames.contains("docs/") && objectNames.contains("images/logo.png")),
                org.mockito.Mockito.eq("bucket-a"),
                org.mockito.Mockito.eq("admin"),
                org.mockito.Mockito.eq(48),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.eq("USER"),
                org.mockito.ArgumentMatchers.<List<Long>>argThat(userIds -> userIds.size() == 2 && userIds.contains(2L) && userIds.contains(3L))
        )).thenReturn(shareLink);

        Result<ShareLink> result = controller.createBatchShare(Map.of(
                "objectNames", List.of("docs/", "images/logo.png"),
                "bucket", "bucket-a",
                "expirationHours", 48,
                "targetType", "USER",
                "recipientUserIds", List.of(2, 3)
        ), new UsernamePasswordAuthenticationToken("admin", "N/A"));

        assertEquals("BATCH001", result.getData().getShareCode());
    }

    @Test
    void shouldCancelShareLink() throws Exception {
        mockMvc.perform(post("/api/share/cancel")
                        .param("shareCode", "CODE1234")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(shareService).cancelShareLink("CODE1234", "admin");
    }
}
