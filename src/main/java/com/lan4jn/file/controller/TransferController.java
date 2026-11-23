package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.dto.TransferDownloadSessionRequest;
import com.lan4jn.file.dto.TransferDownloadSessionResponse;
import com.lan4jn.file.dto.TransferUploadCompleteRequest;
import com.lan4jn.file.dto.TransferUploadSessionRequest;
import com.lan4jn.file.dto.TransferUploadSessionResponse;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.service.TransferProxyService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public TransferController(TransferProxyService transferProxyService) {
        this.transferProxyService = transferProxyService;
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
                                                                   @RequestParam(value = "password", required = false) String password) {
        return transferProxyService.buildArchiveDownloadResponse(id, shareCode, password);
    }
}
