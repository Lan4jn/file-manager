package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.ShareArchivePlan;
import com.lan4jn.file.service.ShareService;
import com.lan4jn.file.utils.RequestValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;
    private final FileService fileService;
    private final PermissionAssertService permissionAssertService;
    private final OperationLogService operationLogService;

    public ShareController(ShareService shareService,
                           FileService fileService,
                           PermissionAssertService permissionAssertService,
                           OperationLogService operationLogService) {
        this.shareService = shareService;
        this.fileService = fileService;
        this.permissionAssertService = permissionAssertService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/create")
    public Result<ShareLink> createShare(@RequestBody CreateShareRequest request, Authentication authentication) {
        request.setObjectName(RequestValidationUtils.requireText(request.getObjectName(), "objectName"));
        request.setBucket(RequestValidationUtils.nullableText(request.getBucket(), "bucket"));
        request.setPassword(RequestValidationUtils.nullableText(request.getPassword(), "password"));
        request.setExpirationHours(RequestValidationUtils.optionalPositive(request.getExpirationHours(), "expirationHours"));
        request.setTargetType(RequestValidationUtils.nullableText(request.getTargetType(), "targetType"));
        if (request.getRecipientUserIds() != null) {
            request.setRecipientUserIds(request.getRecipientUserIds().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        permissionAssertService.assertApiPermission(PermissionCodes.API_SHARE_MANAGE);
        permissionAssertService.assertBucketPermission(resolveBucket(request.getBucket()), BucketAction.SHARE);

        log.info("Creating share link for: {}", request.getObjectName());
        ShareLink shareLink = shareService.createShareLink(request, authentication.getName());
        return Result.success(shareLink);
    }

    @PostMapping("/batch-create")
    @SuppressWarnings("unchecked")
    public Result<ShareLink> createBatchShare(@RequestBody Map<String, Object> params, Authentication authentication) {
        log.info("Creating batch share");
        List<String> objectNames = RequestValidationUtils.requireTextList((List<String>) params.get("objectNames"), "objectNames");
        String bucket = RequestValidationUtils.nullableText((String) params.get("bucket"), "bucket");
        Integer expirationHours = params.get("expirationHours") != null
                ? Integer.valueOf(params.get("expirationHours").toString())
                : null;
        String password = params.get("password") != null
                ? params.get("password").toString()
                : null;
        String targetType = params.get("targetType") != null
                ? params.get("targetType").toString()
                : null;
        List<Long> recipientUserIds = params.get("recipientUserIds") instanceof List<?> rawRecipientUserIds
                ? rawRecipientUserIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> Long.valueOf(item.toString()))
                .collect(Collectors.toList())
                : List.of();

        expirationHours = RequestValidationUtils.optionalPositive(expirationHours, "expirationHours");
        password = RequestValidationUtils.nullableText(password, "password");
        targetType = RequestValidationUtils.nullableText(targetType, "targetType");
        permissionAssertService.assertApiPermission(PermissionCodes.API_SHARE_MANAGE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.SHARE);

        ShareLink shareLink = shareService.createBatchShareLink(
                objectNames, bucket, authentication.getName(), expirationHours, password, targetType, recipientUserIds);
        return Result.success(shareLink);
    }

    @GetMapping("/access-info")
    public Result<ShareAccessInfo> getShareAccessInfo(@RequestParam("shareCode") String shareCode) {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        return Result.success(shareService.getShareAccessInfo(shareCode));
    }

    @GetMapping("/info")
    public Result<ShareLink> getShareInfo(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        password = RequestValidationUtils.nullableText(password, "password");
        return Result.success(shareService.getShareInfo(shareCode, password));
    }

    @GetMapping("/file")
    public Result<FileInfo> getFileByShareCode(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        password = RequestValidationUtils.nullableText(password, "password");
        return Result.success(shareService.getFileByShareCode(shareCode, password));
    }

    @GetMapping("/items")
    public Result<List<FileInfo>> listSharedItems(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "path", required = false) String path) {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        password = RequestValidationUtils.nullableText(password, "password");
        path = RequestValidationUtils.nullableText(path, "path");
        return Result.success(shareService.listSharedItems(shareCode, password, path));
    }

    @GetMapping("/download-item")
    public ResponseEntity<InputStreamResource> downloadSharedItem(
            @RequestParam("shareCode") String shareCode,
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "password", required = false) String password) throws UnsupportedEncodingException {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        password = RequestValidationUtils.nullableText(password, "password");

        FileInfo fileInfo = shareService.getSharedItemInfo(shareCode, password, objectName);
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            throw new RuntimeException("目录请使用批量下载接口");
        }

        ShareLink link = shareService.peekShareInfo(shareCode, password);
        InputStream stream = fileService.getFileStream(
                fileInfo.getObjectName(),
                link.getBucket()
        );
        shareService.incrementViewCount(shareCode);
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/download-all")
    public ResponseEntity<InputStreamResource> downloadAllSharedItems(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest request) throws Exception {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        password = RequestValidationUtils.nullableText(password, "password");

        ShareLink shareLink = shareService.peekShareInfo(shareCode, password);
        if (!Boolean.TRUE.equals(shareLink.getIsFolder())) {
            throw new RuntimeException("仅目录分享支持批量下载");
        }
        ShareArchivePlan plan = shareService.prepareShareArchive(shareCode, password);
        shareService.incrementViewCount(shareCode);
        recordAudit("SHARE", "DOWNLOAD_ALL", "SHARE", shareCode, shareLink.getFileName(), shareLink.getBucket(), shareLink.getObjectName(), request, true, null);

        final ShareArchivePlan validatedPlan = plan;
        final String validatedShareCode = shareCode;
        PipedInputStream stream = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(stream);
        new Thread(() -> {
            try {
                shareService.downloadPreparedShareAsZip(validatedPlan, outputStream);
            } catch (Exception e) {
                log.error("Failed to stream share archive: {}", validatedShareCode, e);
            } finally {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }).start();

        String filename = URLEncoder.encode(shareService.getShareArchiveName(shareCode, password), "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadByShareCode(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws UnsupportedEncodingException {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        password = RequestValidationUtils.nullableText(password, "password");

        log.info("Processing download request for share: {}", shareCode);
        ShareLink link = shareService.peekShareInfo(shareCode, password);
        if (Boolean.TRUE.equals(link.getIsFolder())) {
            throw new RuntimeException("目录分享请使用 download-all 接口");
        }

        FileInfo fileInfo = fileService.getFileInfo(link.getObjectName(), link.getBucket());
        InputStream stream = fileService.getFileStream(link.getObjectName(), link.getBucket());
        shareService.incrementViewCount(shareCode);
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolderShareLink(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        RequestValidationUtils.requireText(shareCode, "shareCode");
        RequestValidationUtils.nullableText(password, "password");
        throw new RuntimeException("目录分享请使用 download-all 接口");
    }

    @GetMapping("/validate")
    public Result<Boolean> validateShareCode(@RequestParam("shareCode") String shareCode) {
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        return Result.success(shareService.validateShareCode(shareCode));
    }

    @DeleteMapping("/delete")
    public Result<Void> deleteShare(@RequestParam("shareCode") String shareCode, Authentication authentication) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_SHARE_MANAGE);
        shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
        log.info("Deleting share link: {}", shareCode);
        shareService.deleteShareLink(shareCode, authentication.getName());
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<ShareLink>> getShareLinks(Authentication authentication) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_SHARE_MANAGE);
        return Result.success(shareService.getShareLinksByUser(authentication.getName()));
    }

    private String resolveBucket(String bucket) {
        return (bucket == null || bucket.isBlank()) ? fileService.getDefaultBucket() : bucket;
    }

    private void recordAudit(String module,
                             String action,
                             String targetType,
                             String targetId,
                             String targetName,
                             String bucketName,
                             String objectName,
                             HttpServletRequest request,
                             boolean success,
                             String errorMessage) {
        if (operationLogService == null) {
            return;
        }
        operationLogService.safeRecord(
                resolveOperatorUsername(),
                module,
                action,
                targetType,
                targetId,
                targetName,
                success,
                errorMessage,
                bucketName,
                objectName,
                request
        );
    }

    private String resolveOperatorUsername() {
        try {
            PermissionSnapshot snapshot = permissionAssertService.currentSnapshot();
            return snapshot != null ? snapshot.username() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
