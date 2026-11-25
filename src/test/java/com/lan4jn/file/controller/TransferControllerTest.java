package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.MultipartUploadCompleteRequest;
import com.lan4jn.file.dto.MultipartUploadInitRequest;
import com.lan4jn.file.dto.MultipartUploadInitResponse;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.repository.TransferArchiveTaskRepository;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.FileDomainSupport;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.TransferArchiveTaskService;
import com.lan4jn.file.service.TransferMultipartUploadService;
import com.lan4jn.file.service.TransferProxyService;
import com.lan4jn.file.service.TransferSessionService;
import com.lan4jn.file.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private TransferMultipartUploadService multipartUploadService;
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
        multipartUploadService = mock(TransferMultipartUploadService.class);
        TransferController controller = new TransferController(transferProxyService, multipartUploadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(fileService.getDefaultBucket()).thenReturn("file-manager");
        when(permissionAssertService.currentSnapshot()).thenReturn(snapshot("alice"));
    }

    @Test
    void shouldBeInstantiableBySpringContainer() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(TransferProxyService.class, () -> mock(TransferProxyService.class));
        context.registerBean(TransferMultipartUploadService.class, () -> mock(TransferMultipartUploadService.class));
        context.registerBean(TransferController.class);

        assertDoesNotThrow(context::refresh);
        context.close();
    }

    @Test
    void shouldDeclareSingleConstructorForSpringInjection() {
        assertEquals(1, TransferController.class.getDeclaredConstructors().length);
    }

    @Test
    void shouldExposeMultipartRoutesAndForwardRawPartBody() throws Exception {
        String basePath = "/api/transfer/upload/multipart";
        byte[] partBody = {1, 2, 3};
        when(multipartUploadService.initSession(any(MultipartUploadInitRequest.class)))
                .thenReturn(new MultipartUploadInitResponse("session-1", "bucket-a", "sample.bin", "sample.bin",
                        10_485_760L, 2, true, List.of(new MultipartUploadInitResponse.UploadedPart(1, "etag-1"))));
        when(multipartUploadService.uploadPart(eq("session-1"), eq(2), any(InputStream.class), eq(3L)))
                .thenAnswer(call -> {
                    InputStream body = call.getArgument(2);
                    assertArrayEquals(partBody, body.readAllBytes());
                    return Map.of("partNumber", 2, "eTag", "etag-2");
                });
        when(multipartUploadService.completeSession(eq("session-1"), any(MultipartUploadCompleteRequest.class)))
                .thenAnswer(call -> {
                    MultipartUploadCompleteRequest request = call.getArgument(1);
                    assertEquals("trace-1", request.getUploadTraceId());
                    assertEquals("etag-2", request.getParts().get(0).getETag());
                    UploadResponse response = new UploadResponse();
                    response.setObjectName("sample.bin");
                    return response;
                });

        mockMvc.perform(post(basePath + "/init").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"sample.bin\",\"size\":10485763}"))
                .andExpect(jsonPath("$.data.uploadedParts[0].eTag").value("etag-1"));
        mockMvc.perform(put(basePath + "/session-1/parts/2").contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(partBody))
                .andExpect(jsonPath("$.data.eTag").value("etag-2"));
        mockMvc.perform(post(basePath + "/session-1/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadTraceId\":\"trace-1\",\"parts\":[{\"partNumber\":2,\"eTag\":\"etag-2\"}]}"))
                .andExpect(jsonPath("$.data.objectName").value("sample.bin"));
        mockMvc.perform(delete(basePath + "/session-1")).andExpect(jsonPath("$.code").value(200));
        verify(multipartUploadService).abortSession("session-1");
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
    }

    @Test
    void shouldAllowDownloadTokenWithoutAuthenticatedContext() throws Exception {
        String token = transferSessionService.issueFileDownloadToken(
                "alice", "bucket-a", "docs/readme.txt", "readme.txt");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));
        when(fileService.getFileStream("docs/readme.txt", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("hello world!".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/transfer/download/stream/{token}", token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world!"));

        verify(permissionAssertService, never()).currentSnapshot();
        verify(permissionAssertService, never()).assertBucketPermission("bucket-a", BucketAction.DOWNLOAD);
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
    void shouldReturnProxyStreamUrlWhenModeIsApplication() throws Exception {
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
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/transfer/download/stream/")))
                .andExpect(jsonPath("$.data.fallbackUrl", containsString("/api/transfer/download/stream/")));
    }

    @Test
    void shouldReturnProxyStreamUrlWhenHybridAndSmallFile() throws Exception {
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
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/transfer/download/stream/")));
    }

    @Test
    void shouldReturnProxyDownloadUrlWhenHybridAndLargeFile() throws Exception {
        when(configService.getDownloadTransportMode()).thenReturn("HYBRID");
        when(configService.getDownloadProxyThresholdBytes()).thenReturn(1024L);
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 4096L));

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","bucket":"bucket-a","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("PROXY"))
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/transfer/download/stream/")));
    }

    @Test
    void shouldReturnDirectDownloadUrlWhenModeIsDirect() throws Exception {
        when(configService.getDownloadTransportMode()).thenReturn("DIRECT");
        when(fileService.getFileInfo("docs/readme.txt", "bucket-a"))
                .thenReturn(fileInfo("docs/readme.txt", "readme.txt", 12L));
        when(fileService.getPresignedDownloadUrl("docs/readme.txt", "bucket-a", "readme.txt"))
                .thenReturn("http://minio/download-url");

        mockMvc.perform(post("/api/transfer/download/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","bucket":"bucket-a","objectName":"docs/readme.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DIRECT"))
                .andExpect(jsonPath("$.data.downloadUrl").value("http://minio/download-url"))
                .andExpect(jsonPath("$.data.fallbackUrl", containsString("/api/transfer/download/stream/")));
    }

    @Test
    void shouldCreateDirectUploadSession() throws Exception {
        when(configService.getUploadTransportMode()).thenReturn("DIRECT");
        when(fileService.prepareDirectUpload("report.pdf", 128L, "docs", "bucket-a"))
                .thenReturn(new FileDomainSupport.PreparedUpload("bucket-a", "docs/report.pdf", "report.pdf", 128L));
        when(fileService.getPresignedUploadUrl("docs/report.pdf", "bucket-a"))
                .thenReturn("http://minio/upload-url");

        mockMvc.perform(post("/api/transfer/upload/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"report.pdf","size":128,"contentType":"application/pdf","folder":"docs","bucket":"bucket-a","uploadTraceId":"trace-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("DIRECT"))
                .andExpect(jsonPath("$.data.uploadTraceId").value("trace-001"))
                .andExpect(jsonPath("$.data.uploadUrl").value("http://minio/upload-url"))
                .andExpect(jsonPath("$.data.uploadToken").isNotEmpty())
                .andExpect(jsonPath("$.data.objectName").value("docs/report.pdf"));
    }

    @Test
    void shouldUseTargetFileNameWhenCreatingUploadSession() throws Exception {
        when(configService.getUploadTransportMode()).thenReturn("DIRECT");
        when(fileService.prepareDirectUpload("renamed-report.pdf", 128L, "docs", "bucket-a"))
                .thenReturn(new FileDomainSupport.PreparedUpload("bucket-a", "docs/renamed-report.pdf", "renamed-report.pdf", 128L));
        when(fileService.getPresignedUploadUrl("docs/renamed-report.pdf", "bucket-a"))
                .thenReturn("http://minio/upload-url");

        mockMvc.perform(post("/api/transfer/upload/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"report.pdf","originalFileName":"report.pdf","targetFileName":"renamed-report.pdf","size":128,"contentType":"application/pdf","folder":"docs","bucket":"bucket-a"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("renamed-report.pdf"))
                .andExpect(jsonPath("$.data.objectName").value("docs/renamed-report.pdf"));
    }

    @Test
    void shouldCompleteDirectUpload() throws Exception {
        when(permissionAssertService.currentSnapshot()).thenReturn(snapshot("alice"));
        UploadResponse uploadResponse = new UploadResponse();
        uploadResponse.setObjectName("docs/report.pdf");
        uploadResponse.setFileName("report.pdf");
        uploadResponse.setSize(128L);
        uploadResponse.setContentType("application/pdf");
        String uploadToken = transferSessionService.issueUploadToken(
                "alice", "bucket-a", "docs/report.pdf", "report.pdf", "application/pdf", 128L, "token-trace");
        when(fileService.completeDirectUpload("bucket-a", "docs/report.pdf", "report.pdf", "application/pdf", 128L, "request-trace"))
                .thenReturn(uploadResponse);

        mockMvc.perform(post("/api/transfer/upload/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadToken\":\"" + uploadToken + "\",\"uploadTraceId\":\"request-trace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectName").value("docs/report.pdf"))
                .andExpect(jsonPath("$.data.fileName").value("report.pdf"));
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
                .andExpect(jsonPath("$.data.downloadUrl", containsString("/api/transfer/download/stream/")));

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
    void shouldRejectArchiveTaskCreationWhenCurrentUsernameIsBlank() throws Exception {
        when(permissionAssertService.currentSnapshot()).thenReturn(snapshot(""));

        mockMvc.perform(post("/api/transfer/archive-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scene":"FILE","taskType":"BATCH","bucket":"bucket-a","objectNames":["docs/readme.txt"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未获取到当前用户"));

        verify(transferArchiveTaskService, never())
                .createBatchArchiveTask(eq(""), eq("bucket-a"), eq(List.of("docs/readme.txt")));
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
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5L))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("batch-download-1.zip")))
                .andExpect(content().string("hello"));
    }

    @Test
    void shouldStreamOnlyTheRequestedArchiveBytes(@TempDir Path directory) throws Exception {
        archiveDownloadMvc(directory).perform(get("/api/transfer/archive-tasks/1/download")
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4L))
                .andExpect(content().bytes(new byte[] {2, 3, 4, 5}));
    }

    @Test
    void shouldRejectAnUnsatisfiableArchiveRange(@TempDir Path directory) throws Exception {
        archiveDownloadMvc(directory).perform(get("/api/transfer/archive-tasks/1/download")
                        .header(HttpHeaders.RANGE, "bytes=10-12"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"))
                .andExpect(content().bytes(new byte[0]));
    }

    private MockMvc archiveDownloadMvc(Path directory) throws IOException {
        Path archive = Files.write(directory.resolve("archive.zip"), new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        TransferArchiveTask ready = task(1L, "COMPLETED", "READY");
        ready.setArchiveLocation(archive.toString());
        ready.setArchiveFileName("archive.zip");
        TransferArchiveTaskRepository repository = mock(TransferArchiveTaskRepository.class);
        when(repository.findByIdAndOwnerUsername(1L, "alice")).thenReturn(Optional.of(ready));
        TransferArchiveTaskService archiveService = new TransferArchiveTaskService(
                repository, fileService, configService, shareService, directory.toString());
        TransferProxyService proxyService = new TransferProxyService(
                configService, fileService, permissionAssertService, transferSessionService, archiveService, shareService);
        return MockMvcBuilders.standaloneSetup(new TransferController(proxyService, multipartUploadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
