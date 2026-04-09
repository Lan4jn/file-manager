package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.TransferArchiveTaskService;
import com.lan4jn.file.service.TransferProxyService;
import com.lan4jn.file.service.TransferSessionService;
import com.lan4jn.file.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerTest {

    private MockMvc mockMvc;
    private ConfigService configService;
    private FileService fileService;
    private PermissionAssertService permissionAssertService;
    private TransferSessionService transferSessionService;
    private TransferArchiveTaskService transferArchiveTaskService;
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        fileService = mock(FileService.class);
        permissionAssertService = mock(PermissionAssertService.class);
        transferArchiveTaskService = mock(TransferArchiveTaskService.class);
        shareService = mock(ShareService.class);
        transferSessionService = new TransferSessionService();
        TransferProxyService transferProxyService = new TransferProxyService(
                configService, fileService, permissionAssertService, transferSessionService, transferArchiveTaskService, shareService);
        TransferController controller = new TransferController(transferProxyService, permissionAssertService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(fileService.getDefaultBucket()).thenReturn("file-manager");
        when(permissionAssertService.currentSnapshot()).thenReturn(snapshot("alice"));
    }

    @Test
    void shouldIssueProxyDownloadSessionForLargeFile() throws Exception {
        when(configService.getDownloadTransportMode()).thenReturn("PROXY");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","bucket":"bucket-a","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("PROXY"))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value(containsString("/api/transfer/download/stream/")));

        verify(permissionAssertService).assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        verify(permissionAssertService).assertBucketPermission("bucket-a", BucketAction.DOWNLOAD);
    }

    @Test
    void shouldStreamFileByDownloadToken() throws Exception {
        String token = transferSessionService.issueFileDownloadToken(
                "alice", "bucket-a", "docs/readme.txt", "readme.txt");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));
        when(fileService.getFileStream("docs/readme.txt", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''readme.txt")))
                .andExpect(content().string("hello world!"));

        verify(permissionAssertService).assertBucketPermission("bucket-a", BucketAction.DOWNLOAD);
    }

    @Test
    void shouldRejectDownloadTokenReusedByAnotherUser() throws Exception {
        String token = transferSessionService.issueFileDownloadToken(
                "alice", "bucket-a", "docs/readme.txt", "readme.txt");
        when(permissionAssertService.currentSnapshot()).thenReturn(snapshot("bob"));

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldSupportRangeDownloadByToken() throws Exception {
        String token = transferSessionService.issueFileDownloadToken(
                "alice", "bucket-a", "docs/readme.txt", "readme.txt");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));
        when(fileService.getFileStream("docs/readme.txt", "bucket-a", 6L, 6L))
                .thenReturn(new ByteArrayInputStream("world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token)
                        .header(HttpHeaders.RANGE, "bytes=6-11"))
                .andExpect(status().is(HttpStatus.PARTIAL_CONTENT.value()))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 6-11/12"))
                .andExpect(content().string("world!"));
    }

    @Test
    void shouldEncodeUnicodeFilenameInContentDisposition() throws Exception {
        String token = transferSessionService.issueFileDownloadToken(
                "alice", "bucket-a", "docs/readme.txt", "中文 空格.txt");
        FileInfo fileInfo = fileInfo("docs/readme.txt", "中文 空格.txt", 12L);
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a")).thenReturn(fileInfo);
        when(fileService.getFileStream("docs/readme.txt", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UTF-8")));
    }

    @Test
    void shouldReturnApplicationDownloadUrlWhenModeIsApplication() throws Exception {
        when(configService.getDownloadTransportMode()).thenReturn("APPLICATION");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","bucket":"bucket-a","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("APPLICATION"))
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/file/download")))
                .andExpect(jsonPath("$.data.downloadUrl", containsString("objectName=docs%2Freadme.txt")));
    }

    @Test
    void shouldReturnApplicationDownloadUrlWhenHybridAndSmallFile() throws Exception {
        when(configService.getDownloadTransportMode()).thenReturn("HYBRID");
        when(configService.getDownloadProxyThresholdBytes()).thenReturn(1024L);
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","bucket":"bucket-a","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("APPLICATION"))
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/file/download")));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/transfer/download/stream/{token}", "bad-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        String token = transferSessionService.issueDownloadToken(
                "FILE", "alice", "bucket-a", "docs/readme.txt", "readme.txt", -1L);

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldExposeShareDownloadSessionMetadata() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setBucket("bucket-a");
        shareLink.setObjectName("docs/readme.txt");
        shareLink.setFileName("readme.txt");
        shareLink.setIsFolder(false);

        when(configService.getDownloadTransportMode()).thenReturn("PROXY");
        when(shareService.getSharedItemInfo("SHARE001", null, "docs/readme.txt"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));
        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"SHARE_ITEM","shareCode":"SHARE001","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("PROXY"))
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/share/download-item")));

        verify(permissionAssertService, org.mockito.Mockito.never()).assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        verify(permissionAssertService, org.mockito.Mockito.never()).assertBucketPermission("bucket-a", BucketAction.DOWNLOAD);
    }

    @Test
    void shouldCreateArchiveTask() throws Exception {
        when(transferArchiveTaskService.createBatchArchiveTask("alice", "bucket-a", List.of("docs/readme.txt")))
                .thenReturn(1L);

        mockMvc.perform(post("/api/transfer/archive-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","taskType":"BATCH","bucket":"bucket-a","objectNames":["docs/readme.txt"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(1L));

        verify(permissionAssertService).assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        verify(permissionAssertService).assertBucketPermission("bucket-a", BucketAction.DOWNLOAD);
    }

    @Test
    void shouldCreateShareArchiveTaskWithoutApiPermission() throws Exception {
        when(transferArchiveTaskService.createShareArchiveTask("SHARE001", null))
                .thenReturn(2L);

        mockMvc.perform(post("/api/transfer/archive-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"SHARE","taskType":"SHARE","shareCode":"SHARE001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(2L));

        verify(permissionAssertService, org.mockito.Mockito.never()).assertApiPermission(PermissionCodes.API_FILE_OPERATE);
    }

    @Test
    void shouldReturnArchiveTaskProgressFromController() throws Exception {
        when(transferArchiveTaskService.getTask(1L, "alice"))
                .thenReturn(task(1L, "RUNNING", "ARCHIVING"));

        mockMvc.perform(get("/api/transfer/archive-tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.stage").value("ARCHIVING"))
                .andExpect(jsonPath("$.data.archiveLocation").doesNotExist())
                .andExpect(jsonPath("$.data.sourceDescriptor").doesNotExist());
    }

    @Test
    void shouldExposeArchiveTaskDownloadUrlWhenCompleted() throws Exception {
        when(transferArchiveTaskService.getTask(1L, "alice"))
                .thenReturn(task(1L, "COMPLETED", "READY"));

        mockMvc.perform(get("/api/transfer/archive-tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/transfer/archive-tasks/1/download"));
    }

    @Test
    void shouldExposeShareArchiveTaskDownloadUrlWhenCompleted() throws Exception {
        when(transferArchiveTaskService.getShareTask(2L, "SHARE001", null))
                .thenReturn(shareTask(2L, "COMPLETED", "READY"));

        mockMvc.perform(get("/api/transfer/archive-tasks/2")
                        .param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl",
                        containsString("/api/transfer/archive-tasks/2/download?shareCode=SHARE001")));
    }

    @Test
    void shouldDownloadArchiveTaskById() throws Exception {
        when(transferArchiveTaskService.openArchiveDownload(1L, "alice", null, null))
                .thenReturn(new TransferArchiveTaskService.ArchiveDownload(
                        "batch-download-1.zip",
                        5L,
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
                ));

        mockMvc.perform(get("/api/transfer/archive-tasks/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("batch-download-1.zip")))
                .andExpect(content().string("hello"));
    }

    private FileInfo fileInfo(String objectName, String fileName, long size) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName(objectName);
        fileInfo.setFileName(fileName);
        fileInfo.setContentType("text/plain");
        fileInfo.setSize(size);
        fileInfo.setIsFolder(false);
        return fileInfo;
    }

    private PermissionSnapshot snapshot(String username) {
        return new PermissionSnapshot(
                1L,
                username,
                false,
                Set.of("USER"),
                Set.of(),
                Set.of(PermissionCodes.API_FILE_OPERATE),
                Map.of("bucket-a", Set.of(BucketAction.DOWNLOAD), BucketAction.ALL_BUCKETS, Set.of(BucketAction.DOWNLOAD))
        );
    }

    private TransferArchiveTask task(Long id, String status, String stage) {
        TransferArchiveTask task = new TransferArchiveTask();
        task.setId(id);
        task.setScene("FILE");
        task.setStatus(status);
        task.setStage(stage);
        task.setOwnerUsername("alice");
        task.setArchiveLocation("/tmp/archive.zip");
        task.setSourceDescriptor("docs/readme.txt");
        return task;
    }

    private TransferArchiveTask shareTask(Long id, String status, String stage) {
        TransferArchiveTask task = new TransferArchiveTask();
        task.setId(id);
        task.setScene("SHARE");
        task.setShareCode("SHARE001");
        task.setStatus(status);
        task.setStage(stage);
        task.setOwnerUsername("__share__");
        return task;
    }
}
