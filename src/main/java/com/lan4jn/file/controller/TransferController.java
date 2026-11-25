package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.MultipartUploadCompleteRequest;
import com.lan4jn.file.dto.MultipartUploadInitRequest;
import com.lan4jn.file.dto.MultipartUploadInitResponse;
import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.dto.TransferDownloadSessionRequest;
import com.lan4jn.file.dto.TransferDownloadSessionResponse;
import com.lan4jn.file.dto.TransferUploadCompleteRequest;
import com.lan4jn.file.dto.TransferUploadSessionRequest;
import com.lan4jn.file.dto.TransferUploadSessionResponse;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.service.TransferMultipartUploadService;
import com.lan4jn.file.service.TransferProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private final TransferProxyService transferProxyService;
    private final TransferMultipartUploadService multipartUploadService;

    public TransferController(TransferProxyService transferProxyService,
                              TransferMultipartUploadService multipartUploadService) {
        this.transferProxyService = transferProxyService;
        this.multipartUploadService = multipartUploadService;
    }

    @PostMapping("/download/session")
    public Result<TransferDownloadSessionResponse> createDownloadSession(
            @RequestBody TransferDownloadSessionRequest request) {
        return Result.success(transferProxyService.createDownloadSession(request));
    }

    @PostMapping("/upload/session")
    public Result<TransferUploadSessionResponse> createUploadSession(
            @RequestBody TransferUploadSessionRequest request) {
        return Result.success(transferProxyService.createUploadSession(request));
    }

    @PostMapping("/upload/complete")
    public Result<UploadResponse> completeUpload(@RequestBody TransferUploadCompleteRequest request) {
        return Result.success(transferProxyService.completeUpload(request.getUploadToken(), request.getUploadTraceId()));
    }

    @PostMapping("/upload/multipart/init")
    public Result<MultipartUploadInitResponse> initMultipartUpload(@RequestBody MultipartUploadInitRequest request)
            throws Exception {
        return Result.success(multipartUploadService.initSession(request));
    }

    @PutMapping("/upload/multipart/{sessionId}/parts/{partNumber}")
    public Result<Map<String, Object>> uploadPart(@PathVariable("sessionId") String sessionId,
                                                   @PathVariable("partNumber") int partNumber,
                                                   HttpServletRequest request) throws Exception {
        long contentLength = request.getContentLengthLong();
        try (var body = request.getInputStream()) {
            return Result.success(multipartUploadService.uploadPart(sessionId, partNumber, body, contentLength));
        }
    }

    @PostMapping("/upload/multipart/{sessionId}/complete")
    public Result<UploadResponse> completeMultipartUpload(@PathVariable("sessionId") String sessionId,
                                                          @RequestBody(required = false) MultipartUploadCompleteRequest request)
            throws Exception {
        return Result.success(multipartUploadService.completeSession(sessionId, request));
    }

    @DeleteMapping("/upload/multipart/{sessionId}")
    public Result<Void> abortMultipartUpload(@PathVariable("sessionId") String sessionId) {
        multipartUploadService.abortSession(sessionId);
        return Result.success();
    }

    @GetMapping("/download/stream/{token}")
    public ResponseEntity<InputStreamResource> streamDownload(
            @PathVariable("token") String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        return transferProxyService.buildDownloadResponse(token, rangeHeader);
    }

    @PostMapping("/archive-tasks")
    public Result<Map<String, Object>> createArchiveTask(@RequestBody TransferArchiveTaskRequest request) {
        Long taskId = transferProxyService.createArchiveTask(request);
        return Result.success(Map.of("taskId", taskId));
    }

    @GetMapping("/archive-tasks/{id}")
    public Result<TransferArchiveTask> getArchiveTask(@PathVariable("id") Long id,
                                                      @RequestParam(value = "shareCode", required = false) String shareCode,
                                                      @RequestParam(value = "password", required = false) String password) {
        return Result.success(transferProxyService.getArchiveTask(id, shareCode, password));
    }

    @GetMapping("/archive-tasks/{id}/download")
    public ResponseEntity<InputStreamResource> downloadArchiveTask(@PathVariable("id") Long id,
                                                                   @RequestParam(value = "shareCode", required = false) String shareCode,
                                                                   @RequestParam(value = "password", required = false) String password,
                                                                   @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        return transferProxyService.buildArchiveDownloadResponse(id, shareCode, password, rangeHeader);
    }

    public ResponseEntity<InputStreamResource> downloadArchiveTask(Long id, String shareCode, String password) {
        return transferProxyService.buildArchiveDownloadResponse(id, shareCode, password);
    }
}
