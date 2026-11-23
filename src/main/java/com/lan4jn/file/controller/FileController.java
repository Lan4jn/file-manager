package com.lan4jn.file.controller;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.FileTransferExecuteRequest;
import com.lan4jn.file.dto.FileTransferExecuteResponse;
import com.lan4jn.file.dto.FileTransferPreviewRequest;
import com.lan4jn.file.dto.FileTransferPreviewResponse;
import com.lan4jn.file.dto.FileRenameRequest;
import com.lan4jn.file.dto.FileRenameResponse;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.BucketStatsRefreshStatus;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.FileTransferService;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.utils.HttpRangeUtils;
import com.lan4jn.file.utils.RequestValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final ConfigService configService;
    private final PermissionAssertService permissionAssertService;
    private final OperationLogService operationLogService;
    private final FileTransferService fileTransferService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public FileController(FileService fileService,
                          ConfigService configService,
                          PermissionAssertService permissionAssertService,
                          OperationLogService operationLogService,
                          FileTransferService fileTransferService) {
        this.fileService = fileService;
        this.configService = configService;
        this.permissionAssertService = permissionAssertService;
        this.operationLogService = operationLogService;
        this.fileTransferService = fileTransferService;
    }

    @GetMapping("/buckets")
    public Result<List<BucketInfo>> listBuckets(
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        log.info("Listing buckets, sortBy: {}, order: {}", sortBy, order);
        PermissionSnapshot snapshot = permissionAssertService.currentSnapshot();
        if (snapshot.superAdmin() || hasGlobalBucketAccess(snapshot)) {
            return Result.success(fileService.listBuckets(sortBy, order));
        }
        List<String> accessibleBuckets = snapshot.bucketPermissions().entrySet().stream()
                .filter(entry -> !BucketAction.ALL_BUCKETS.equals(entry.getKey()))
                .filter(entry -> hasBucketAccess(entry.getValue()))
                .map(Map.Entry::getKey)
                .distinct()
                .toList();
        if (accessibleBuckets.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(fileService.listBuckets(accessibleBuckets, sortBy, order));
    }

    @PostMapping("/buckets/stats/refresh")
    public Result<Map<String, Object>> refreshBucketStats() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_BUCKET_MANAGE);
        boolean started = fileService.triggerBucketStatsRefresh();
        log.info("Trigger bucket stats refresh, started: {}", started);
        return Result.success(Map.of(
                "started", started,
                "message", started ? "桶统计刷新任务已启动" : "桶统计刷新任务已在执行中"
        ));
    }

    @GetMapping("/buckets/stats/status")
    public Result<BucketStatsRefreshStatus> getBucketStatsRefreshStatus() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        return Result.success(fileService.getBucketStatsRefreshStatus());
    }

    @PostMapping("/buckets")
    public Result<BucketInfo> createBucket(@RequestBody Map<String, String> request) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_BUCKET_MANAGE);
        String bucketName = RequestValidationUtils.requireText(request.get("bucketName"), "bucketName");
        return Result.success(fileService.createBucket(bucketName));
    }

    @DeleteMapping("/buckets")
    public Result<Void> deleteBucket(@RequestParam("bucketName") String bucketName) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_BUCKET_MANAGE);
        bucketName = RequestValidationUtils.requireText(bucketName, "bucketName");
        fileService.deleteBucket(bucketName);
        return Result.success();
    }

    @GetMapping("/buckets/default")
    public Result<String> getDefaultBucket() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        return Result.success(fileService.getDefaultBucket());
    }

    @GetMapping("/upload-config")
    public Result<Map<String, Object>> getUploadConfig() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        Map<String, Object> config = new HashMap<>();
        config.put("allowedExtensions", configService.getAllowedExtensions());
        config.put("maxFileSize", configService.getMaxFileSize());
        config.put("uploadDialogMode", configService.getUploadDialogMode());
        config.put("fileDropMode", configService.getFileDropMode());
        return Result.success(config);
    }

    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "targetFileName", required = false, defaultValue = "") String targetFileName,
            @RequestParam(value = "uploadTraceId", required = false, defaultValue = "") String uploadTraceId) {
        folder = RequestValidationUtils.optionalText(folder, "folder");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        targetFileName = RequestValidationUtils.optionalText(targetFileName, "targetFileName");
        uploadTraceId = RequestValidationUtils.optionalText(uploadTraceId, "uploadTraceId");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.UPLOAD);
        log.info("Upload request received, traceId: {}, originalFileName: {}, targetFileName: {}, size: {}, contentType: {}, folder: {}, bucket: {}",
                uploadTraceId, file.getOriginalFilename(), targetFileName, file.getSize(), file.getContentType(), folder, bucket);
        return Result.success(fileService.uploadFile(file, folder, bucket, targetFileName, uploadTraceId));
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order,
            @RequestParam(value = "folderFirst", required = false, defaultValue = "true") boolean folderFirst,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size,
            Authentication authentication) {
        folder = RequestValidationUtils.optionalText(folder, "folder");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.LIST_FILE);
        log.info("Listing files, folder: {}, bucket: {}, sortBy: {}, order: {}, page: {}, size: {}",
                folder, bucket, sortBy, order, page, size);

        if (page <= 0 || size <= 0) {
            Result<?> result = Result.success(fileService.listFiles(folder, bucket, sortBy, order));
            recordDirectoryHistory(authentication, bucket, folder);
            return result;
        }

        PageResult<FileInfo> pageResult = fileService.listFiles(folder, bucket, sortBy, order, folderFirst, page, size);
        recordDirectoryHistory(authentication, bucket, folder);
        return Result.success(pageResult);
    }

    @GetMapping("/info")
    public Result<FileInfo> info(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.LIST_FILE);
        return Result.success(fileService.getFileInfo(objectName, bucket));
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DELETE);
        log.info("Deleting file: {}, bucket: {}", objectName, bucket);
        fileService.deleteFile(objectName, bucket);
        return Result.success();
    }

    @DeleteMapping("/batch-delete")
    public Result<Void> batchDelete(
            @RequestParam("objectNames") String objectNamesStr,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.BATCH);
        List<String> objectNames = RequestValidationUtils.requireTextList(
                Arrays.asList(objectNamesStr.split(",")), "objectNames");
        log.info("Batch deleting {} files, bucket: {}", objectNames.size(), bucket);
        fileService.deleteFiles(objectNames, bucket);
        return Result.success();
    }

    @PostMapping("/transfer/preview")
    public Result<FileTransferPreviewResponse> previewTransfer(@RequestBody FileTransferPreviewRequest request) {
        String bucket = RequestValidationUtils.optionalText(request.getBucket(), "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.BATCH);
        return Result.success(fileTransferService.preview(request));
    }

    @PostMapping("/transfer/execute")
    public Result<FileTransferExecuteResponse> executeTransfer(@RequestBody FileTransferExecuteRequest request) {
        String bucket = RequestValidationUtils.optionalText(request.getBucket(), "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.BATCH);
        return Result.success(fileTransferService.execute(request));
    }

    @PostMapping("/folder")
    public Result<Void> createFolder(
            @RequestParam("path") String path,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "createTestFile", required = false, defaultValue = "true") boolean createTestFile,
            HttpServletRequest request) {
        path = RequestValidationUtils.requireText(path, "path");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.CREATE_FOLDER);
        log.info("Creating folder: {}, bucket: {}, createTestFile: {}", path, bucket, createTestFile);
        try {
            fileService.createFolder(path, bucket, createTestFile);
            recordAudit("FILE", "CREATE_FOLDER", "FOLDER", path, path, bucket, path, request, true, null);
        } catch (RuntimeException e) {
            recordAudit("FILE", "CREATE_FOLDER", "FOLDER", path, path, bucket, path, request, false, e.getMessage());
            throw e;
        }
        return Result.success();
    }

    @PostMapping("/rename")
    public Result<FileRenameResponse> rename(@RequestBody FileRenameRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String sourcePath = RequestValidationUtils.requireText(request.getSourcePath(), "sourcePath");
        String targetName = RequestValidationUtils.requireText(request.getTargetName(), "targetName");
        String bucket = RequestValidationUtils.optionalText(request.getBucket(), "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.BATCH);
        return Result.success(fileService.rename(sourcePath, targetName, bucket, request.getConflictPolicy()));
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws UnsupportedEncodingException {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        log.info("Downloading file: {}, bucket: {}", objectName, bucket);

        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        return buildFileDownloadResponse(fileInfo, objectName, bucket, rangeHeader);
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket)
            throws Exception {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        log.info("Downloading folder: {}, bucket: {}", objectName, bucket);
        List<FileInfo> files = fileService.resolveFolderArchiveEntries(objectName, bucket);

        String folderName = objectName.endsWith("/") ? objectName.substring(0, objectName.length() - 1) : objectName;
        int lastSlash = folderName.lastIndexOf('/');
        if (lastSlash >= 0) {
            folderName = folderName.substring(lastSlash + 1);
        }
        if (folderName.isEmpty()) {
            folderName = "folder";
        }

        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);

        final List<FileInfo> validatedFiles = files;
        final String validatedBucket = bucket;
        final String validatedObjectName = objectName;
        executorService.submit(() -> {
            try {
                fileService.downloadResolvedFilesAsZip(validatedFiles, validatedBucket, pipedOut);
            } catch (Exception e) {
                log.error("Failed to create ZIP for folder: {}", validatedObjectName, e);
            } finally {
                try {
                    pipedOut.close();
                } catch (Exception ignored) {
                }
            }
        });

        String filename = URLEncoder.encode(folderName + ".zip", "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(pipedIn));
    }

    @GetMapping("/batch-download")
    public ResponseEntity<InputStreamResource> batchDownload(
            @RequestParam("objectNames") String objectNamesStr,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket)
            throws Exception {
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.BATCH);
        List<String> objectNames = RequestValidationUtils.requireTextList(
                Arrays.asList(objectNamesStr.split(",")), "objectNames");
        log.info("Batch downloading {} items, bucket: {}", objectNames.size(), bucket);
        List<FileInfo> files = fileService.resolveBatchArchiveEntries(objectNames, bucket);
        long totalSize = files.stream().mapToLong(file -> file.getSize() != null ? file.getSize() : 0L).sum();

        Long maxSize = configService.getBatchDownloadMaxSize();
        if (maxSize > 0 && totalSize > maxSize) {
            throw new RuntimeException("批量下载总大小超过限制(" + formatSize(maxSize) + ")");
        }

        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);
        final List<FileInfo> validatedFiles = files;
        final String validatedBucket = bucket;
        executorService.submit(() -> {
            try {
                fileService.downloadResolvedFilesAsZip(validatedFiles, validatedBucket, pipedOut);
            } catch (Exception e) {
                log.error("Failed to create batch ZIP", e);
            } finally {
                try {
                    pipedOut.close();
                } catch (Exception ignored) {
                }
            }
        });

        String filename = URLEncoder.encode("batch-download-" + objectNames.size() + ".zip", "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(pipedIn));
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            HttpServletRequest request)
            throws UnsupportedEncodingException {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        log.info("Previewing file: {}, bucket: {}", objectName, bucket);

        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        fileService.assertPreviewSupported(fileInfo);
        InputStream stream = fileService.getFileStream(objectName, bucket);
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");
        recordAudit("FILE", "PREVIEW", "FILE", objectName, fileInfo.getFileName(), bucket, objectName, request, true, null);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/preview-url")
    public Result<String> getPreviewUrl(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.nullableText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        return Result.success(fileService.getPreviewUrl(objectName, bucket));
    }

    @PostMapping("/copy")
    public Result<Void> copy(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName,
            @RequestParam(value = "sourceBucket", required = false, defaultValue = "") String sourceBucket,
            @RequestParam(value = "destBucket", required = false, defaultValue = "") String destBucket) {
        sourceObjectName = RequestValidationUtils.requireText(sourceObjectName, "source");
        destObjectName = RequestValidationUtils.requireText(destObjectName, "dest");
        sourceBucket = RequestValidationUtils.optionalText(sourceBucket, "sourceBucket");
        destBucket = RequestValidationUtils.optionalText(destBucket, "destBucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(sourceBucket), BucketAction.DOWNLOAD);
        permissionAssertService.assertBucketPermission(resolveBucket(destBucket), BucketAction.UPLOAD);
        log.info("Copying file from {} to {}", sourceObjectName, destObjectName);
        fileService.copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket);
        return Result.success();
    }

    @PostMapping("/move")
    public Result<Void> move(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName,
            @RequestParam(value = "sourceBucket", required = false, defaultValue = "") String sourceBucket,
            @RequestParam(value = "destBucket", required = false, defaultValue = "") String destBucket) {
        sourceObjectName = RequestValidationUtils.requireText(sourceObjectName, "source");
        destObjectName = RequestValidationUtils.requireText(destObjectName, "dest");
        sourceBucket = RequestValidationUtils.optionalText(sourceBucket, "sourceBucket");
        destBucket = RequestValidationUtils.optionalText(destBucket, "destBucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(sourceBucket), BucketAction.DELETE);
        permissionAssertService.assertBucketPermission(resolveBucket(destBucket), BucketAction.UPLOAD);
        log.info("Moving file from {} to {}", sourceObjectName, destObjectName);
        fileService.moveFile(sourceObjectName, destObjectName, sourceBucket, destBucket);
        return Result.success();
    }

    private String resolveBucket(String bucket) {
        return (bucket == null || bucket.isBlank()) ? fileService.getDefaultBucket() : bucket;
    }

    private ResponseEntity<InputStreamResource> buildFileDownloadResponse(
            FileInfo fileInfo, String objectName, String bucket, String rangeHeader) throws UnsupportedEncodingException {
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");
        MediaType mediaType = MediaType.parseMediaType(fileInfo.getContentType());
        long fileSize = fileInfo.getSize() == null ? 0L : fileInfo.getSize();

        try {
            HttpRangeUtils.ByteRange range = HttpRangeUtils.parseSingleRange(rangeHeader, fileSize);
            if (range == null) {
                InputStream stream = fileService.getFileStream(objectName, bucket);
                return ResponseEntity.ok()
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .contentType(mediaType)
                        .contentLength(fileSize)
                        .body(new InputStreamResource(stream));
            }

            InputStream stream = fileService.getFileStream(objectName, bucket, range.getStart(), range.getLength());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, range.toContentRange())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
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

    private boolean hasGlobalBucketAccess(PermissionSnapshot snapshot) {
        return snapshot.bucketPermissions().containsKey(BucketAction.ALL_BUCKETS);
    }

    private boolean hasBucketAccess(Set<String> actions) {
        return actions != null;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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

    private void recordDirectoryHistory(Authentication authentication, String bucket, String folder) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return;
        }
        if (folder == null || folder.isBlank()) {
            return;
        }
        try {
            fileService.recordDirectoryAccess(authentication.getName(), bucket, folder);
        } catch (RuntimeException ex) {
            log.warn("Record directory history failed for user={}, bucket={}, folder={}, error={}",
                    authentication.getName(), bucket, folder, ex.getMessage());
        }
    }
}
