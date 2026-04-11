package com.lan4jn.file.service;

import com.lan4jn.file.dto.TransferArchiveTaskRequest;
import com.lan4jn.file.dto.TransferDownloadSessionRequest;
import com.lan4jn.file.dto.TransferDownloadSessionResponse;
import com.lan4jn.file.dto.TransferUploadSessionRequest;
import com.lan4jn.file.dto.TransferUploadSessionResponse;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.TransportMode;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.utils.HttpRangeUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public class TransferProxyService {
    private static final String SHARE_DOWNLOAD_OWNER = "__share__";

    private final ConfigService configService;
    private final FileService fileService;
    private final PermissionAssertService permissionAssertService;
    private final TransferSessionService transferSessionService;
    private final TransferArchiveTaskService transferArchiveTaskService;
    private final ShareService shareService;

    public TransferProxyService(ConfigService configService,
                                FileService fileService,
                                PermissionAssertService permissionAssertService,
                                TransferSessionService transferSessionService,
                                TransferArchiveTaskService transferArchiveTaskService,
                                ShareService shareService) {
        this.configService = configService;
        this.fileService = fileService;
        this.permissionAssertService = permissionAssertService;
        this.transferSessionService = transferSessionService;
        this.transferArchiveTaskService = transferArchiveTaskService;
        this.shareService = shareService;
    }

    public TransferDownloadSessionResponse createDownloadSession(TransferDownloadSessionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String scene = normalizeText(request.getScene(), "FILE").toUpperCase(Locale.ROOT);
        if ("FILE".equals(scene)) {
            permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
            String objectName = requireText(request.getObjectName(), "objectName");
            String bucket = resolveBucket(request.getBucket());
            permissionAssertService.assertBucketPermission(bucket, BucketAction.DOWNLOAD);
            FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
            return buildFileSceneDownloadSession(scene, bucket, objectName, fileInfo);
        }

        if ("SHARE_FILE".equals(scene)) {
            String shareCode = requireText(request.getShareCode(), "shareCode");
            String password = nullableText(request.getPassword());
            shareService.peekShareInfo(shareCode, password);
            FileInfo fileInfo = shareService.getFileByShareCode(shareCode, password);
            ShareLink link = shareService.peekShareInfo(shareCode, password);
            return buildShareSceneDownloadSession(fileInfo, link.getBucket(), buildShareFileDownloadUrl(shareCode, password));
        }

        if ("SHARE_ITEM".equals(scene)) {
            String shareCode = requireText(request.getShareCode(), "shareCode");
            String objectName = requireText(request.getObjectName(), "objectName");
            String password = nullableText(request.getPassword());
            shareService.peekShareInfo(shareCode, password);
            FileInfo fileInfo = shareService.getSharedItemInfo(shareCode, password, objectName);
            ShareLink link = shareService.peekShareInfo(shareCode, password);
            return buildShareSceneDownloadSession(fileInfo, link.getBucket(), buildShareItemDownloadUrl(shareCode, password, objectName));
        }

        throw new IllegalArgumentException("不支持的下载场景");
    }

    public TransferUploadSessionResponse createUploadSession(TransferUploadSessionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        String bucket = resolveBucket(request.getBucket());
        permissionAssertService.assertBucketPermission(bucket, BucketAction.UPLOAD);
        String operatorUsername = permissionAssertService.currentSnapshot().username();
        FileDomainSupport.PreparedUpload preparedUpload = fileService.prepareDirectUpload(
                requireText(request.getFileName(), "fileName"),
                request.getSize(),
                nullableText(request.getFolder()),
                bucket
        );

        TransportMode mode = resolveUploadMode(request.getSize());
        TransferUploadSessionResponse response = new TransferUploadSessionResponse();
        response.setMode(mode);
        response.setBucket(preparedUpload.getBucket());
        response.setObjectName(preparedUpload.getObjectName());
        response.setFileName(preparedUpload.getFileName());
        response.setContentType(nullableText(request.getContentType()));
        if (mode == TransportMode.DIRECT) {
            response.setUploadUrl(fileService.getPresignedUploadUrl(preparedUpload.getObjectName(), preparedUpload.getBucket()));
            response.setUploadToken(transferSessionService.issueUploadToken(
                    operatorUsername,
                    preparedUpload.getBucket(),
                    preparedUpload.getObjectName(),
                    preparedUpload.getFileName(),
                    nullableText(request.getContentType()),
                    request.getSize()
            ));
            response.setHeaders(buildDirectUploadHeaders(request.getContentType()));
            return response;
        }

        response.setUploadUrl("/api/file/upload");
        response.setHeaders(Map.of());
        return response;
    }

    public UploadResponse completeUpload(String uploadToken) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        String operatorUsername = permissionAssertService.currentSnapshot().username();
        TransferSessionService.UploadTicket ticket = transferSessionService.requireUploadTicket(uploadToken, operatorUsername);
        permissionAssertService.assertBucketPermission(resolveBucket(ticket.getBucket()), BucketAction.UPLOAD);
        return fileService.completeDirectUpload(
                ticket.getBucket(),
                ticket.getObjectName(),
                ticket.getFileName(),
                ticket.getContentType(),
                ticket.getSize()
        );
    }

    public ResponseEntity<InputStreamResource> buildDownloadResponse(String token, String rangeHeader) {
        TransferSessionService.DownloadTicket ticket = transferSessionService.requireDownloadTicket(token);
        String scene = ticket.getScene() == null ? "FILE" : ticket.getScene().toUpperCase(Locale.ROOT);
        if (!"FILE".equals(scene)) {
            throw new IllegalArgumentException("当前仅支持 FILE 场景");
        }
        String bucket = resolveBucket(ticket.getBucket());
        FileInfo fileInfo = fileService.getFileInfo(ticket.getObjectName(), bucket);
        return buildFileDownloadResponse(fileInfo, ticket.getObjectName(), bucket, rangeHeader);
    }

    public Long createArchiveTask(TransferArchiveTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String scene = normalizeText(request.getScene(), "FILE").toUpperCase(Locale.ROOT);
        if ("SHARE".equals(scene)) {
            Long taskId = transferArchiveTaskService.createShareArchiveTask(
                    requireText(request.getShareCode(), "shareCode"),
                    nullableText(request.getPassword())
            );
            transferArchiveTaskService.triggerPendingTasksAsync();
            return taskId;
        }

        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        String bucket = resolveBucket(request.getBucket());
        permissionAssertService.assertBucketPermission(bucket, BucketAction.DOWNLOAD);
        String operatorUsername = permissionAssertService.currentSnapshot().username();
        Long taskId;
        if ("BATCH".equals(normalizeText(request.getTaskType(), "").toUpperCase(Locale.ROOT))) {
            taskId = transferArchiveTaskService.createBatchArchiveTask(operatorUsername, bucket, request.getObjectNames());
        } else if ("FOLDER".equals(normalizeText(request.getTaskType(), "").toUpperCase(Locale.ROOT))) {
            taskId = transferArchiveTaskService.createFolderArchiveTask(operatorUsername, bucket, request.getObjectName());
        } else {
            taskId = transferArchiveTaskService.createTask(operatorUsername, request);
        }
        transferArchiveTaskService.triggerPendingTasksAsync();
        return taskId;
    }

    public TransferArchiveTask getArchiveTask(Long taskId, String shareCode, String password) {
        TransferArchiveTask task;
        if (shareCode != null && !shareCode.trim().isEmpty()) {
            task = transferArchiveTaskService.getShareTask(taskId, shareCode, nullableText(password));
        } else {
            permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
            String operatorUsername = permissionAssertService.currentSnapshot().username();
            task = transferArchiveTaskService.getTask(taskId, operatorUsername);
        }
        decorateArchiveDownloadUrl(task, shareCode, password);
        return task;
    }

    public ResponseEntity<InputStreamResource> buildArchiveDownloadResponse(Long taskId, String shareCode, String password) {
        String operatorUsername = null;
        if (shareCode == null || shareCode.trim().isEmpty()) {
            permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
            operatorUsername = permissionAssertService.currentSnapshot().username();
        }
        TransferArchiveTaskService.ArchiveDownload archiveDownload =
                transferArchiveTaskService.openArchiveDownload(taskId, operatorUsername, nullableText(shareCode), nullableText(password));
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(archiveDownload.fileName(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType("application/zip"));
        if (archiveDownload.contentLength() != null) {
            responseBuilder.contentLength(archiveDownload.contentLength());
        }
        return responseBuilder.body(new InputStreamResource(archiveDownload.inputStream()));
    }

    private TransferDownloadSessionResponse buildFileSceneDownloadSession(
            String scene, String bucket, String objectName, FileInfo fileInfo) {
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            throw new IllegalArgumentException("目录请使用归档下载");
        }

        TransportMode mode = resolveDownloadMode(fileInfo.getSize());
        TransferDownloadSessionResponse response = new TransferDownloadSessionResponse();
        response.setMode(mode);
        response.setFileName(fileInfo.getFileName());
        response.setUseTask(false);
        response.setTaskId(null);
        response.setFallbackUrl(buildProxyDownloadUrl(
                scene,
                permissionAssertService.currentSnapshot().username(),
                bucket,
                objectName,
                fileInfo.getFileName()
        ));
        if (mode == TransportMode.PROXY) {
            response.setDownloadUrl(response.getFallbackUrl());
            return response;
        }
        if (mode == TransportMode.DIRECT) {
            response.setDownloadUrl(fileService.getPresignedDownloadUrl(objectName, bucket, fileInfo.getFileName()));
            return response;
        }

        response.setDownloadUrl(buildApplicationDownloadUrl(bucket, objectName));
        return response;
    }

    private TransferDownloadSessionResponse buildShareSceneDownloadSession(FileInfo fileInfo, String bucket, String downloadUrl) {
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            throw new IllegalArgumentException("目录请使用归档下载");
        }

        TransferDownloadSessionResponse response = new TransferDownloadSessionResponse();
        TransportMode mode = resolveDownloadMode(fileInfo.getSize());
        response.setMode(mode);
        response.setFileName(fileInfo.getFileName());
        response.setUseTask(false);
        response.setTaskId(null);
        response.setFallbackUrl(buildProxyDownloadUrl(
                "FILE",
                SHARE_DOWNLOAD_OWNER,
                requireText(bucket, "bucket"),
                requireText(fileInfo.getObjectName(), "objectName"),
                requireText(fileInfo.getFileName(), "fileName")
        ));
        if (mode == TransportMode.PROXY) {
            response.setDownloadUrl(response.getFallbackUrl());
            return response;
        }
        if (mode == TransportMode.DIRECT) {
            response.setDownloadUrl(fileService.getPresignedDownloadUrl(
                    requireText(fileInfo.getObjectName(), "objectName"),
                    bucket,
                    requireText(fileInfo.getFileName(), "fileName")
            ));
            return response;
        }
        response.setDownloadUrl(downloadUrl);
        return response;
    }

    private TransportMode resolveDownloadMode(Long fileSize) {
        TransportMode configured = TransportMode.from(configService.getDownloadTransportMode());
        if (configured != TransportMode.HYBRID) {
            return configured;
        }
        long size = fileSize == null ? 0L : fileSize;
        long threshold = configService.getDownloadProxyThresholdBytes();
        return size >= threshold ? TransportMode.PROXY : TransportMode.APPLICATION;
    }

    private TransportMode resolveUploadMode(Long fileSize) {
        TransportMode configured = TransportMode.from(configService.getUploadTransportMode());
        if (configured == TransportMode.PROXY) {
            return TransportMode.APPLICATION;
        }
        if (configured != TransportMode.HYBRID) {
            return configured;
        }
        long size = fileSize == null ? 0L : fileSize;
        long threshold = configService.getUploadProxyThresholdBytes();
        return size >= threshold ? TransportMode.DIRECT : TransportMode.APPLICATION;
    }

    private String buildApplicationDownloadUrl(String bucket, String objectName) {
        String encodedObject = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
        String encodedBucket = URLEncoder.encode(bucket, StandardCharsets.UTF_8);
        return "/api/file/download?objectName=" + encodedObject + "&bucket=" + encodedBucket;
    }

    private String buildShareFileDownloadUrl(String shareCode, String password) {
        StringBuilder builder = new StringBuilder("/api/share/download?shareCode=")
                .append(URLEncoder.encode(shareCode, StandardCharsets.UTF_8));
        appendOptionalQuery(builder, "password", password);
        return builder.toString();
    }

    private String buildShareItemDownloadUrl(String shareCode, String password, String objectName) {
        StringBuilder builder = new StringBuilder("/api/share/download-item?shareCode=")
                .append(URLEncoder.encode(shareCode, StandardCharsets.UTF_8))
                .append("&objectName=")
                .append(URLEncoder.encode(objectName, StandardCharsets.UTF_8));
        appendOptionalQuery(builder, "password", password);
        return builder.toString();
    }

    private String buildProxyDownloadUrl(String scene,
                                         String ownerUsername,
                                         String bucket,
                                         String objectName,
                                         String fileName) {
        String token = transferSessionService.issueDownloadToken(
                scene,
                ownerUsername,
                requireText(bucket, "bucket"),
                requireText(objectName, "objectName"),
                requireText(fileName, "fileName")
        );
        return "/api/transfer/download/stream/" + token;
    }

    private ResponseEntity<InputStreamResource> buildFileDownloadResponse(
            FileInfo fileInfo, String objectName, String bucket, String rangeHeader) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(fileInfo.getFileName(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = safeMediaType(fileInfo.getContentType());
        long fileSize = fileInfo.getSize() == null ? 0L : fileInfo.getSize();

        try {
            HttpRangeUtils.ByteRange range = HttpRangeUtils.parseSingleRange(rangeHeader, fileSize);
            if (range == null) {
                InputStream stream = fileService.getFileStream(objectName, bucket);
                return ResponseEntity.ok()
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                        .contentType(mediaType)
                        .contentLength(fileSize)
                        .body(new InputStreamResource(stream));
            }

            InputStream stream = fileService.getFileStream(objectName, bucket, range.getStart(), range.getLength());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, range.toContentRange())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .contentType(mediaType)
                    .contentLength(range.getLength())
                    .body(new InputStreamResource(stream));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                    .build();
        }
    }

    private MediaType safeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String nullableText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void appendOptionalQuery(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("&")
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append("=")
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String resolveBucket(String bucket) {
        if (bucket != null && !bucket.trim().isEmpty()) {
            return bucket.trim();
        }
        return fileService.getDefaultBucket();
    }

    private Map<String, String> buildDirectUploadHeaders(String contentType) {
        String resolved = nullableText(contentType);
        if (resolved == null) {
            return Map.of();
        }
        return Map.of(HttpHeaders.CONTENT_TYPE, resolved);
    }

    private void decorateArchiveDownloadUrl(TransferArchiveTask task, String shareCode, String password) {
        if (task == null || !"COMPLETED".equals(task.getStatus()) || !"READY".equals(task.getStage())) {
            return;
        }
        StringBuilder builder = new StringBuilder("/api/transfer/archive-tasks/")
                .append(task.getId())
                .append("/download");
        String resolvedShareCode = shareCode;
        if ((resolvedShareCode == null || resolvedShareCode.isBlank()) && "SHARE".equals(task.getScene())) {
            resolvedShareCode = task.getShareCode();
        }
        if (resolvedShareCode != null && !resolvedShareCode.isBlank()) {
            builder.append("?shareCode=").append(URLEncoder.encode(resolvedShareCode, StandardCharsets.UTF_8));
            if (password != null && !password.isBlank()) {
                builder.append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8));
            }
        }
        task.setDownloadUrl(builder.toString());
    }
}
