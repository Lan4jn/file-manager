package com.lan4jn.file.controller;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.utils.RequestValidationUtils;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public FileController(FileService fileService,
                          ConfigService configService,
                          PermissionAssertService permissionAssertService) {
        this.fileService = fileService;
        this.configService = configService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/buckets")
    public Result<List<BucketInfo>> listBuckets(
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        PermissionSnapshot snapshot = permissionAssertService.currentSnapshot();
        if (snapshot.superAdmin() || hasGlobalBucketListPermission(snapshot)) {
            return Result.success(fileService.listBuckets(sortBy, order));
        }
        List<String> accessibleBuckets = snapshot.bucketPermissions().entrySet().stream()
                .filter(entry -> !BucketAction.ALL_BUCKETS.equals(entry.getKey()))
                .filter(entry -> hasBucketListPermission(entry.getValue()))
                .map(Map.Entry::getKey)
                .distinct()
                .toList();
        if (accessibleBuckets.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(fileService.listBuckets(accessibleBuckets, sortBy, order));
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

    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        folder = RequestValidationUtils.optionalText(folder, "folder");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.UPLOAD);
        log.info("Uploading file: {}, folder: {}, bucket: {}", file.getOriginalFilename(), folder, bucket);
        return Result.success(fileService.uploadFile(file, folder, bucket));
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        folder = RequestValidationUtils.optionalText(folder, "folder");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.LIST_FILE);
        log.info("Listing files, folder: {}, bucket: {}, sortBy: {}, order: {}, page: {}, size: {}",
                folder, bucket, sortBy, order, page, size);

        if (page <= 0 || size <= 0) {
            return Result.success(fileService.listFiles(folder, bucket, sortBy, order));
        }

        PageResult<FileInfo> pageResult = fileService.listFiles(folder, bucket, sortBy, order, page, size);
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

    @PostMapping("/folder")
    public Result<Void> createFolder(
            @RequestParam("path") String path,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "createTestFile", required = false, defaultValue = "true") boolean createTestFile) {
        path = RequestValidationUtils.requireText(path, "path");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.CREATE_FOLDER);
        log.info("Creating folder: {}, bucket: {}, createTestFile: {}", path, bucket, createTestFile);
        fileService.createFolder(path, bucket, createTestFile);
        return Result.success();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket)
            throws UnsupportedEncodingException {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        log.info("Downloading file: {}, bucket: {}", objectName, bucket);

        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
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
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket)
            throws UnsupportedEncodingException {
        objectName = RequestValidationUtils.requireText(objectName, "objectName");
        bucket = RequestValidationUtils.optionalText(bucket, "bucket");
        permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
        permissionAssertService.assertBucketPermission(resolveBucket(bucket), BucketAction.DOWNLOAD);
        log.info("Previewing file: {}, bucket: {}", objectName, bucket);

        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

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

    private boolean hasGlobalBucketListPermission(PermissionSnapshot snapshot) {
        return hasBucketListPermission(snapshot.bucketPermissions().get(BucketAction.ALL_BUCKETS));
    }

    private boolean hasBucketListPermission(Set<String> actions) {
        return actions != null && (actions.contains(BucketAction.LIST_BUCKET) || actions.contains(BucketAction.ALL_ACTIONS));
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
}
